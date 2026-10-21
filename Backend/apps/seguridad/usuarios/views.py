from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.db import transaction
from django.shortcuts import get_object_or_404
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework.exceptions import ValidationError
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView
from drf_yasg.utils import swagger_auto_schema

from apps.catalogos.empleado.models import Empleado
from rest_framework import serializers
from .serializers import (
    UsuarioAdministrativoSerializer,
    EuropartesTokenObtainPairSerializer, EuropartesTokenRefreshSerializer,
)
from .permissions import PuedeAdministrarUsuarios
from .roles import ROLES_EUROPARTES, rol_unico_usuario

User = get_user_model()


def _assign_employee(user, employee_id):
    """Mantiene la relación OneToOne opcional sin reasignaciones duplicadas."""
    Empleado.objects.filter(usuario=user).update(usuario=None)
    if employee_id is None:
        return
    employee = get_object_or_404(Empleado.objects.select_for_update(), pk=employee_id)
    if employee.usuario_id not in (None, user.pk):
        raise ValidationError({"empleado_id": "Ese empleado ya está vinculado a otra cuenta."})
    employee.usuario = user
    employee.save(update_fields=["usuario"])


def _create_admin_user(serializer):
    data = dict(serializer.validated_data)
    employee_id = data.pop("empleado_id", None)
    role = data.pop("rol")
    password = data.pop("password")
    data.pop("password2", None)
    with transaction.atomic():
        user = User.objects.create_user(password=password, **data)
        group, _ = Group.objects.get_or_create(name=role)
        user.groups.remove(*Group.objects.filter(name__in=ROLES_EUROPARTES))
        user.groups.add(group)
        _assign_employee(user, employee_id)
    return user


class EuropartesTokenObtainPairView(TokenObtainPairView):
    permission_classes = [AllowAny]
    serializer_class = EuropartesTokenObtainPairSerializer


class EuropartesTokenRefreshView(TokenRefreshView):
    permission_classes = [AllowAny]
    serializer_class = EuropartesTokenRefreshSerializer


class UserCreateView(APIView):
    """Ruta heredada de registro, ahora protegida y con rol obligatorio."""
    permission_classes = [PuedeAdministrarUsuarios]

    @swagger_auto_schema(request_body=UsuarioAdministrativoSerializer)
    def post(self, request):
        serializer = UsuarioAdministrativoSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = _create_admin_user(serializer)
        return Response(UsuarioAdministrativoSerializer(user).data, status=status.HTTP_201_CREATED)


class UsuariosAdministracionAPIView(APIView):
    permission_classes = [PuedeAdministrarUsuarios]

    def get(self, request):
        users = User.objects.all().order_by("username")
        serializer = UsuarioAdministrativoSerializer(users, many=True)
        return Response(serializer.data)

    @swagger_auto_schema(request_body=UsuarioAdministrativoSerializer)
    def post(self, request):
        serializer = UsuarioAdministrativoSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = _create_admin_user(serializer)
        return Response(UsuarioAdministrativoSerializer(user).data, status=status.HTTP_201_CREATED)


class UsuarioAdministracionDetalleAPIView(APIView):
    permission_classes = [PuedeAdministrarUsuarios]

    def get_object(self, pk):
        return get_object_or_404(User, pk=pk)

    def get(self, request, pk):
        return Response(UsuarioAdministrativoSerializer(self.get_object(pk)).data)

    @swagger_auto_schema(request_body=UsuarioAdministrativoSerializer)
    def put(self, request, pk):
        return self._update(request, pk, partial=False)

    @swagger_auto_schema(request_body=UsuarioAdministrativoSerializer)
    def patch(self, request, pk):
        return self._update(request, pk, partial=True)

    def _update(self, request, pk, partial):
        user = self.get_object(pk)
        serializer = UsuarioAdministrativoSerializer(user, data=request.data, partial=partial)
        serializer.is_valid(raise_exception=True)
        data = dict(serializer.validated_data)
        employee_id = data.pop("empleado_id", MissingValue())
        role = data.pop("rol", None)
        password = data.pop("password", None)
        data.pop("password2", None)
        if user.pk == request.user.pk and data.get("is_active") is False:
            raise ValidationError({"is_active": "No puede desactivar su propia cuenta desde esta pantalla."})
        if user.pk == request.user.pk and role and role != rol_unico_usuario(user):
            raise ValidationError({"rol": "No puede cambiar su propio rol desde esta pantalla."})
        target_active = data.get("is_active", user.is_active)
        if user.is_active and rol_unico_usuario(user) == "Soporte TI" and (role and role != "Soporte TI" or target_active is False):
            another_admin = User.objects.filter(is_active=True, groups__name="Soporte TI").exclude(pk=user.pk).exists()
            if not another_admin:
                raise ValidationError({"rol": "Debe existir al menos una cuenta activa de Soporte TI."})
        with transaction.atomic():
            for field in ("username", "email", "first_name", "last_name", "is_active"):
                if field in data:
                    setattr(user, field, data[field])
            if password:
                user.set_password(password)
            user.save()
            if role:
                group, _ = Group.objects.get_or_create(name=role)
                user.groups.remove(*Group.objects.filter(name__in=ROLES_EUROPARTES))
                user.groups.add(group)
            elif len(list(user.groups.filter(name__in=ROLES_EUROPARTES))) != 1:
                raise ValidationError({"rol": "La cuenta debe tener exactamente un rol funcional."})
            if not isinstance(employee_id, MissingValue):
                _assign_employee(user, employee_id)
        return Response(UsuarioAdministrativoSerializer(user).data)




class RestablecerClaveSerializer(serializers.Serializer):
    password = serializers.CharField(write_only=True, required=True, validators=[] , trim_whitespace=False)
    password2 = serializers.CharField(write_only=True, required=True, trim_whitespace=False)

    def validate(self, attrs):
        from django.contrib.auth.password_validation import validate_password
        from django.core.exceptions import ValidationError as DjangoValidationError
        if attrs["password"] != attrs["password2"]:
            raise ValidationError({"password": "Las contraseñas no coinciden."})
        try:
            validate_password(attrs["password"], self.context.get("user"))
        except DjangoValidationError as exc:
            raise serializers.ValidationError({"password": exc.messages})
        return attrs


class UsuarioRestablecerClaveAPIView(APIView):
    permission_classes = [PuedeAdministrarUsuarios]

    def post(self, request, pk):
        user = get_object_or_404(User, pk=pk)
        serializer = RestablecerClaveSerializer(data=request.data, context={"user": user})
        serializer.is_valid(raise_exception=True)
        user.set_password(serializer.validated_data["password"])
        user.save(update_fields=["password"])
        return Response({"detail": "Contraseña restablecida correctamente."}, status=status.HTTP_200_OK)


class UsuarioEstadoAPIView(APIView):
    permission_classes = [PuedeAdministrarUsuarios]

    def patch(self, request, pk):
        if "is_active" not in request.data or not isinstance(request.data["is_active"], bool):
            raise ValidationError({"is_active": "Este campo booleano es obligatorio."})
        user = get_object_or_404(User, pk=pk)
        if user.pk == request.user.pk and request.data["is_active"] is False:
            raise ValidationError({"is_active": "No puede desactivar su propia cuenta desde esta pantalla."})
        if user.is_active and rol_unico_usuario(user) == "Soporte TI" and request.data["is_active"] is False:
            another_admin = User.objects.filter(is_active=True, groups__name="Soporte TI").exclude(pk=user.pk).exists()
            if not another_admin:
                raise ValidationError({"is_active": "Debe existir al menos una cuenta activa de Soporte TI."})
        user.is_active = request.data["is_active"]
        user.save(update_fields=["is_active"])
        return Response(UsuarioAdministrativoSerializer(user).data)


class MissingValue:
    pass
