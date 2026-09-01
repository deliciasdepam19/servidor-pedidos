-- Tabla de dispositivos para push notifications
-- Ejecutar en Supabase SQL Editor

CREATE TABLE IF NOT EXISTS dispositivos (
  id SERIAL PRIMARY KEY,
  telefono TEXT NOT NULL,
  expo_push_token TEXT NOT NULL,
  plataforma TEXT DEFAULT 'android',
  activo BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(telefono, expo_push_token)
);

CREATE INDEX IF NOT EXISTS idx_dispositivos_telefono ON dispositivos(telefono);
CREATE INDEX IF NOT EXISTS idx_dispositivos_activo ON dispositivos(activo);

ALTER TABLE dispositivos ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow all" ON dispositivos
  FOR ALL
  USING (true)
  WITH CHECK (true);
