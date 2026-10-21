from django.urls import path
from .views import (
    UserCreateView, UsuariosAdministracionAPIView,
    UsuarioAdministracionDetalleAPIView, UsuarioRestablecerClaveAPIView,
    UsuarioEstadoAPIView,
)

urlpatterns = [
    path('api/v1/register/', UserCreateView.as_view(), name='register-user'),
    path('api/v1/admin/', UsuariosAdministracionAPIView.as_view(), name='admin-users-list-create'),
    path('api/v1/admin/<int:pk>/', UsuarioAdministracionDetalleAPIView.as_view(), name='admin-user-detail'),
    path('api/v1/admin/<int:pk>/estado/', UsuarioEstadoAPIView.as_view(), name='admin-user-status'),
    path('api/v1/admin/<int:pk>/password/', UsuarioRestablecerClaveAPIView.as_view(), name='admin-user-password'),
]
