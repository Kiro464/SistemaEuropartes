from .models import User
from rest_framework import serializers
from django.contrib.auth.password_validation import validate_password
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer


class UserCreateSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, required=True, validators=[validate_password])
    password2 = serializers.CharField(write_only=True, required=True)

    class Meta:
        model = User
        fields = ('username', 'password', 'password2', 'email', 'first_name', 'last_name')

    # Validar que ambas contraseñas coincidan
    def validate(self, attrs):
        if attrs['password'] != attrs['password2']:
            raise serializers.ValidationError({"password": "Las contraseñas no coinciden."})
        return attrs

    # Crear el usuario
    def create(self, validated_data):
        user = User(
            username=validated_data['username'],
            email=validated_data['email'],
            first_name=validated_data['first_name'],
            last_name=validated_data['last_name']
        )
        user.set_password(validated_data['password'])  # Encriptar la contraseña
        user.save()
        return user


class EuropartesTokenObtainPairSerializer(TokenObtainPairSerializer):
    """
    Identidad JWT basada únicamente en User (esquema actual).
    No consulta Empleado: Empleado.UsuarioId pertenece a Fase 1B.
    """

    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        token['user_id'] = user.id
        token['username'] = user.username
        token['nombres'] = user.first_name
        token['apellidos'] = user.last_name
        group = user.groups.first()
        token['grupo'] = group.name if group else None
        return token
