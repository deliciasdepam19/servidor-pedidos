-- Notificaciones: lectura por teléfono + alcance por teléfono
-- Ejecutar en Supabase SQL Editor (una sola vez)

-- ── 1. Columna "telefono" en notificaciones_app ──────────────────────
-- NULL      = aviso global (campanita de la panadería): lo ven todos.
-- 9 dígitos = notificación dirigida (cambio de estado del pedido):
--             solo ese cliente la ve en su campanita.

ALTER TABLE notificaciones_app
  ADD COLUMN IF NOT EXISTS telefono TEXT;

CREATE INDEX IF NOT EXISTS idx_notificaciones_app_telefono
  ON notificaciones_app(telefono);

CREATE INDEX IF NOT EXISTS idx_notificaciones_app_created_at
  ON notificaciones_app(created_at);

-- ── 2. Tabla de lectura por teléfono ─────────────────────────────────
-- El estado "leída" es POR TELÉFONO (no por dispositivo): una notificación
-- global la ven todos los clientes, por eso el "leída" de uno no puede
-- marcar la notificación como leída para el resto. Tabla puente.

CREATE TABLE IF NOT EXISTS notificaciones_leidas (
  id SERIAL PRIMARY KEY,
  notificacion_id INTEGER NOT NULL,
  telefono TEXT NOT NULL,
  leida_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(notificacion_id, telefono)
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_leidas_telefono
  ON notificaciones_leidas(telefono);

CREATE INDEX IF NOT EXISTS idx_notificaciones_leidas_notificacion
  ON notificaciones_leidas(notificacion_id);

ALTER TABLE notificaciones_leidas ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow all" ON notificaciones_leidas
  FOR ALL
  USING (true)
  WITH CHECK (true);
