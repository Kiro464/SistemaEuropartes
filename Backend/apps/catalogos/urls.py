from django.urls import path, include

urlpatterns = [
    path('pais/', include('apps.catalogos.pais.urls')),
    path('departamento/', include('apps.catalogos.departamento.urls')),
    path('proveedor/', include('apps.catalogos.proveedor.urls')),
    path('cliente/', include('apps.catalogos.cliente.urls')),
    path('empleado/', include('apps.catalogos.empleado.urls')),
    path('marca/', include('apps.catalogos.marca.urls')),
    path('producto/', include('apps.catalogos.producto.urls')),
]