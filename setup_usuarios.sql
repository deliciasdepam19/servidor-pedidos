-- Tabla de usuarios para autenticación OTP
-- Ejecutar en Supabase SQL Editor

CREATE TABLE IF NOT EXISTS usuarios (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  nombre TEXT,
  telefono TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_usuarios_email ON usuarios(email);

ALTER TABLE usuarios ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow all" ON usuarios
  FOR ALL
  USING (true)
  WITH CHECK (true);
