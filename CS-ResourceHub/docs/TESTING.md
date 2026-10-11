# Lista rápida de pruebas

1. Sin configuración, la aplicación debe abrir y mostrar las fichas de ejemplo.
2. Buscar una palabra inexistente debe mostrar el estado vacío; limpiar filtros restaura catálogo.
3. Guardar/quitar una ficha debe conservar la selección tras reabrir la aplicación.
4. Configurar URL/clave incorrectas debe mostrar error y conservar la app en modo demo.
5. Registro con correo y contraseña debe crear un perfil; si se exige confirmación, mostrar el mensaje apropiado.
6. Usuario A crea recurso con archivo pequeño: debe quedar `pending` y no aparecer en catálogo público.
7. Usuario B no debe poder leer archivo privado ni cambiar el estado o el owner del recurso A.
8. Admin aprueba el recurso: aparece en búsqueda pública y su descarga gratuita genera URL firmada de corta duración.
9. Recurso premium no debe permitir descargar sin una compra `paid`.
10. Reseña propia no puede editar otra reseña ni dejar una valoración en su propio recurso.
11. Reporte se crea para revisión; un usuario normal no puede marcarlo revisado.
12. Simular webhook Stripe con firma inválida debe responder 400; no debe marcar compras `paid`.
13. Probar APK en Android con selector de archivo, volver atrás, pérdida de conectividad y reinicio.
14. Probar cada regla RLS con anon, usuario, autor, moderador y admin antes de publicar.
