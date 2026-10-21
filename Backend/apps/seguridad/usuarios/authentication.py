from rest_framework.exceptions import AuthenticationFailed
from rest_framework_simplejwt.authentication import JWTAuthentication
from .roles import rol_unico_usuario


class EuropartesJWTAuthentication(JWTAuthentication):
    """Rechaza access tokens si la cuenta está inactiva o su rol cambió."""
    def get_user(self, validated_token):
        user = super().get_user(validated_token)
        current_role = rol_unico_usuario(user)
        token_role = validated_token.get("grupo")
        if current_role is None or token_role != current_role:
            raise AuthenticationFailed(
                "La autorización de esta sesión cambió. Renueve la sesión para continuar.",
                code="role_changed",
            )
        return user
