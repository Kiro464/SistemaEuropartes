from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers
from rest_framework.exceptions import AuthenticationFailed
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer, TokenRefreshSerializer
from rest_framework_simplejwt.tokens import AccessToken, RefreshToken
from django.contrib.auth import get_user_model

from apps.catalogos.empleado.models import Empleado
from .roles import ROLES_EUROPARTES, roles_usuario, rol_unico_usuario

User = get_user_model()


class UserCreateSerializer(serializers.ModelSerializer):
    """Serializer legado; la ruta register actual usa el serializer administrativo protegido."""
    password = serializers.CharField(write_only=True, required=True, validators=[validate_password])
    password2 = serializers.CharField(write_only=True, required=True)

    class Meta:
        model = User
        fields = ('username', 'password', 'password2', 'email', 'first_name', 'last_name')

    def validate(self, attrs):
        if attrs['password'] != attrs['password2']:
            raise serializers.ValidationError({"password": "Las contraseñas no coinciden."})
        return attrs

    def create(self, validated_data):
        validated_data.pop('password2')
        password = validated_data.pop('password')
        return User.objects.create_user(password=password, **validated_data)


class UsuarioAdministrativoSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    username = serializers.CharField(max_length=150)
    email = serializers.EmailField(allow_blank=True, required=False)
    first_name = serializers.CharField(max_length=150, allow_blank=True, required=False)
    last_name = serializers.CharField(max_length=150, allow_blank=True, required=False)
    is_active = serializers.BooleanField(required=False, default=True)
    rol = serializers.ChoiceField(choices=[(role, role) for role in ROLES_EUROPARTES])
    empleado_id = serializers.IntegerField(allow_null=True, required=False, default=None)
    password = serializers.CharField(write_only=True, required=False, validators=[validate_password], trim_whitespace=False)
    password2 = serializers.CharField(write_only=True, required=False, trim_whitespace=False)

    def validate_username(self, value):
        qs = User.objects.filter(username__iexact=value.strip())
        if self.instance:
            qs = qs.exclude(pk=self.instance.pk)
        if qs.exists():
            raise serializers.ValidationError("Ya existe una cuenta con ese nombre de usuario.")
        return value.strip()

    def validate_empleado_id(self, value):
        if value is None:
            return None
        try:
            empleado = Empleado.objects.get(pk=value)
        except Empleado.DoesNotExist:
            raise serializers.ValidationError("El empleado seleccionado no existe.")
        if self.instance:
            occupied = Empleado.objects.filter(pk=value, usuario__isnull=False).exclude(usuario_id=self.instance.pk).exists()
        else:
            occupied = Empleado.objects.filter(pk=value, usuario__isnull=False).exists()
        if occupied:
            raise serializers.ValidationError("Ese empleado ya está vinculado a otra cuenta.")
        return empleado.pk

    def validate(self, attrs):
        password = attrs.get("password")
        password2 = attrs.get("password2")
        if self.instance is None and not password:
            raise serializers.ValidationError({"password": "La contraseña inicial es obligatoria."})
        if password or password2:
            if not password or not password2:
                raise serializers.ValidationError({"password2": "Confirme la contraseña."})
            if password != password2:
                raise serializers.ValidationError({"password": "Las contraseñas no coinciden."})
        attrs.pop("password2", None)
        return attrs

    def to_representation(self, instance):
        role = rol_unico_usuario(instance)
        employee = Empleado.objects.filter(usuario=instance).only("id").first()
        return {
            "id": instance.pk,
            "username": instance.username,
            "email": instance.email,
            "first_name": instance.first_name,
            "last_name": instance.last_name,
            "is_active": instance.is_active,
            "rol": role,
            "empleado_id": employee.pk if employee else None,
            "rol_valido": role is not None,
        }


class EuropartesTokenObtainPairSerializer(TokenObtainPairSerializer):
    """Emite tokens solo para cuentas con exactamente un rol funcional."""

    def validate(self, attrs):
        data = super().validate(attrs)
        roles = roles_usuario(self.user)
        if len(roles) != 1:
            raise AuthenticationFailed("La cuenta debe tener exactamente un rol válido de Europartes. Contacte a Soporte TI.")
        data_user = self.user
        data["user"] = {
            "user_id": data_user.pk,
            "username": data_user.username,
            "nombres": data_user.first_name,
            "apellidos": data_user.last_name,
            "grupo": roles[0],
        }
        return data

    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        token['user_id'] = user.id
        token['username'] = user.username
        token['nombres'] = user.first_name
        token['apellidos'] = user.last_name
        token['grupo'] = rol_unico_usuario(user)
        return token


class EuropartesTokenRefreshSerializer(TokenRefreshSerializer):
    """Refresca tokens con el rol vigente; bloquea cuentas sin rol único o inactivas."""

    def validate(self, attrs):
        incoming_refresh = RefreshToken(attrs["refresh"])
        user_id_claim = incoming_refresh.get("user_id")
        try:
            user = User.objects.get(pk=user_id_claim)
        except (User.DoesNotExist, TypeError, ValueError):
            raise AuthenticationFailed("La cuenta asociada al token ya no existe.")
        if not user.is_active:
            raise AuthenticationFailed("La cuenta está desactivada.")
        role = rol_unico_usuario(user)
        if role is None:
            raise AuthenticationFailed("La cuenta no tiene exactamente un rol válido. Contacte a Soporte TI.")

        data = super().validate(attrs)
        access = AccessToken(data["access"])
        access["grupo"] = role
        access["user_id"] = user.pk
        access["username"] = user.username
        access["nombres"] = user.first_name
        access["apellidos"] = user.last_name
        data["access"] = str(access)

        if data.get("refresh"):
            refreshed = RefreshToken(data["refresh"])
            refreshed["grupo"] = role
            refreshed["user_id"] = user.pk
            refreshed["username"] = user.username
            refreshed["nombres"] = user.first_name
            refreshed["apellidos"] = user.last_name
            data["refresh"] = str(refreshed)
        return data
