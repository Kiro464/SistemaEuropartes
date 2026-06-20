from django.urls import path
from .views import UserCreateView
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

urlpatterns = [
    path('api/v1/register/', UserCreateView.as_view(), name='register-user'),
    path('api/seguridad/usuarios/token/', TokenObtainPairView.as_view()),
    path('token/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
]