# Hoja de ruta — CS ResourceHub

## Entregado en esta base
- [x] Interfaz Android adaptable y navegación
- [x] Catálogo demo, búsqueda, filtros y guardados
- [x] Cuenta mediante Supabase Auth (correo/contraseña)
- [x] SQL con RLS para perfiles, recursos, favoritos, reseñas, reportes y compras
- [x] Subida de archivo a almacenamiento privado
- [x] Moderación de publicaciones pendientes
- [x] Valoraciones, reportes y descargas firmadas temporales
- [x] Código base de Edge Functions para Stripe Connect Express, Checkout y webhook (pendiente de pruebas reales)
- [x] Workflow de GitHub Actions para compilar la APK

## Antes de producción
- [ ] Conectar y probar proyecto Supabase real
- [ ] Pruebas de seguridad de todas las políticas RLS con varias cuentas
- [ ] Antivirus/escaneo de archivos, reglas contra malware y revisión humana
- [ ] Validar Stripe Connect y repartición de pagos en modo test con varias cuentas
- [ ] Reversión de transferencias en reembolsos, disputas, términos de licencia, derechos de autor e impuestos
- [ ] URL HTTPS pública de retorno para Checkout y política de privacidad
- [ ] Pruebas de carga, cuota de almacenamiento y límites de spam
- [ ] Crear firma de release y probar instalación en Android real
- [ ] Añadir notificaciones de estado de revisión y ventas a los creadores
- [ ] Editar/gestionar versiones, eliminar publicaciones y apelar rechazos

No publiques un marketplace de pago al público hasta completar la lista de producción.
