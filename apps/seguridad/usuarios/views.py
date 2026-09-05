from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework_simplejwt.views import TokenObtainPairView
from .serializers import UserCreateSerializer, EuropartesTokenObtainPairSerializer
from drf_yasg.utils import swagger_auto_schema


class EuropartesTokenObtainPairView(TokenObtainPairView):
    permission_classes = [AllowAny]
    serializer_class = EuropartesTokenObtainPairSerializer


class UserCreateView(APIView):
    @swagger_auto_schema(request_body=UserCreateSerializer)
    def post(self, request):
        serializer = UserCreateSerializer(data=request.data)

        # Validar los datos
        if serializer.is_valid():
            serializer.save()  # Crear el usuario
            return Response({"message": "Usuario creado exitosamente"}, status=status.HTTP_201_CREATED)

        # En caso de error, retornar las validaciones
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
