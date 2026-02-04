-- MEADS Initial Schema
-- Database schema for Mead Evaluation and Awards Data System

-- ============================================================================
-- CORE ENTITIES
-- ============================================================================

-- User: Core identity for all participants
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  display_name VARCHAR(255),
  display_country VARCHAR(255),
  is_system_admin BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_email ON users(email);

-- AccessToken: Magic link tokens for authentication
CREATE TABLE access_tokens (
  id UUID PRIMARY KEY,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  purpose VARCHAR(50) NOT NULL, -- LOGIN, JUDGING_SESSION
  email VARCHAR(255) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMP NOT NULL,
  user_id UUID REFERENCES users(id),
  competition_id UUID -- FK added later after competition table created
);

CREATE INDEX idx_access_tokens_token_hash ON access_tokens(token_hash);
CREATE INDEX idx_access_tokens_email ON access_tokens(email);
CREATE INDEX idx_access_tokens_user_id ON access_tokens(user_id);

-- ============================================================================
-- EVENT & COMPETITION
-- ============================================================================

-- Event: Container for competitions
CREATE TABLE events (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL UNIQUE,
  timezone VARCHAR(50) NOT NULL,
  starts_at DATE,
  ends_at DATE,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_events_slug ON events(slug);

-- ScoringSystem: Defines scoring methodology
CREATE TABLE scoring_systems (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  version VARCHAR(50) NOT NULL,
  description TEXT,
  max_total_points INTEGER NOT NULL,
  is_default BOOLEAN NOT NULL DEFAULT false,
  enabled BOOLEAN NOT NULL DEFAULT true,
  locked BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT uq_scoring_system_name_version UNIQUE (name, version)
);

-- ScoringComponent: Sub-components of a scoring system
CREATE TABLE scoring_components (
  id UUID PRIMARY KEY,
  scoring_system_id UUID NOT NULL REFERENCES scoring_systems(id),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  max_points INTEGER NOT NULL,
  display_order INTEGER NOT NULL,
  CONSTRAINT uq_scoring_component_system_name UNIQUE (scoring_system_id, name),
  CONSTRAINT uq_scoring_component_system_order UNIQUE (scoring_system_id, display_order)
);

CREATE INDEX idx_scoring_components_system ON scoring_components(scoring_system_id);

-- CategoryTemplate: Reusable category templates
CREATE TABLE category_templates (
  id UUID PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  display_order INTEGER NOT NULL DEFAULT 0
);

-- Join table for ScoringSystem <-> CategoryTemplate many-to-many
CREATE TABLE scoring_system_category_templates (
  scoring_system_id UUID NOT NULL REFERENCES scoring_systems(id),
  category_template_id UUID NOT NULL REFERENCES category_templates(id),
  PRIMARY KEY (scoring_system_id, category_template_id)
);

CREATE INDEX idx_sscm_scoring_system ON scoring_system_category_templates(scoring_system_id);
CREATE INDEX idx_sscm_category_template ON scoring_system_category_templates(category_template_id);

-- Competition: A single competition within an event
CREATE TABLE competitions (
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL REFERENCES events(id),
  scoring_system_id UUID NOT NULL REFERENCES scoring_systems(id),
  name VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL, -- COMMERCIAL, HOME
  registration_opens_at TIMESTAMP,
  registration_closes_at TIMESTAMP,
  status VARCHAR(50) NOT NULL, -- DRAFT, REGISTRATION_OPEN, REGISTRATION_CLOSED, JUDGING, COMPLETED, PUBLISHED
  judging_mode VARCHAR(50) NOT NULL DEFAULT 'INDIVIDUAL', -- INDIVIDUAL, CONSENSUS
  max_score_difference INTEGER,
  entry_code_prefix VARCHAR(10),
  entry_code_format VARCHAR(50) NOT NULL DEFAULT 'SEQUENTIAL', -- SEQUENTIAL, CATEGORY_PREFIXED
  next_entry_number INTEGER NOT NULL DEFAULT 1,
  bos_entries_per_category INTEGER NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_competitions_event ON competitions(event_id);
CREATE INDEX idx_competitions_scoring_system ON competitions(scoring_system_id);
CREATE INDEX idx_competitions_status ON competitions(status);

-- Add foreign key constraint to access_tokens now that competitions table exists
ALTER TABLE access_tokens ADD CONSTRAINT fk_access_tokens_competition
  FOREIGN KEY (competition_id) REFERENCES competitions(id);

CREATE INDEX idx_access_tokens_competition ON access_tokens(competition_id);

-- JudgingDay: Days when judging occurs
CREATE TABLE judging_days (
  id UUID PRIMARY KEY,
  competition_id UUID NOT NULL REFERENCES competitions(id),
  date DATE NOT NULL,
  description VARCHAR(255),
  display_order INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uq_judging_day_competition_date UNIQUE (competition_id, date)
);

CREATE INDEX idx_judging_days_competition ON judging_days(competition_id);

-- ============================================================================
-- COMPETITION ROLES & ASSIGNMENTS
-- ============================================================================

-- CompetitionAdmin: User with admin access to a competition
CREATE TABLE competition_admins (
  id UUID PRIMARY KEY,
  competition_id UUID NOT NULL REFERENCES competitions(id),
  user_id UUID NOT NULL REFERENCES users(id),
  assigned_by_id UUID REFERENCES users(id),
  assigned_at TIMESTAMP NOT NULL,
  CONSTRAINT uq_competition_admin UNIQUE (competition_id, user_id)
);

CREATE INDEX idx_competition_admins_competition ON competition_admins(competition_id);
CREATE INDEX idx_competition_admins_user ON competition_admins(user_id);

-- CompetitionSteward: User with read-only steward access
CREATE TABLE competition_stewards (
  id UUID PRIMARY KEY,
  competition_id UUID NOT NULL REFERENCES competitions(id),
  user_id UUID NOT NULL REFERENCES users(id),
  assigned_by_id UUID REFERENCES users(id),
  assigned_at TIMESTAMP NOT NULL,
  CONSTRAINT uq_competition_steward UNIQUE (competition_id, user_id)
);

CREATE INDEX idx_competition_stewards_competition ON competition_stewards(competition_id);
CREATE INDEX idx_competition_stewards_user ON competition_stewards(user_id);

-- Category: Competition-specific category (copied from template or custom)
CREATE TABLE categories (
  id UUID PRIMARY KEY,
  competition_id UUID NOT NULL REFERENCES competitions(id),
  source_template_id UUID REFERENCES category_templates(id),
  code VARCHAR(50) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  display_order INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uq_category_competition_code UNIQUE (competition_id, code)
);

CREATE INDEX idx_categories_competition ON categories(competition_id);
CREATE INDEX idx_categories_template ON categories(source_template_id);

-- ============================================================================
-- JUDGING INFRASTRUCTURE
-- ============================================================================

-- JudgingTable: Physical/logical table for judging
CREATE TABLE judging_tables (
  id UUID PRIMARY KEY,
  competition_id UUID NOT NULL REFERENCES competitions(id),
  name VARCHAR(255) NOT NULL,
  location VARCHAR(255),
  round VARCHAR(50) NOT NULL DEFAULT 'CATEGORY', -- CATEGORY, MEDAL, BOS
  is_medal_round BOOLEAN NOT NULL DEFAULT false,
  min_advancing INTEGER NOT NULL DEFAULT 1,
  max_advancing INTEGER NOT NULL DEFAULT 3,
  display_order INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uq_judging_table_competition_name UNIQUE (competition_id, name)
);

CREATE INDEX idx_judging_tables_competition ON judging_tables(competition_id);

-- JudgeCompetitionAssignment: Judge in competition pool
CREATE TABLE judge_competition_assignments (
  id UUID PRIMARY KEY,
  competition_id UUID NOT NULL REFERENCES competitions(id),
  user_id UUID NOT NULL REFERENCES users(id),
  assigned_by_id UUID REFERENCES users(id),
  assigned_at TIMESTAMP NOT NULL,
  notes TEXT,
  CONSTRAINT uq_judge_competition UNIQUE (competition_id, user_id)
);

CREATE INDEX idx_judge_comp_assignments_competition ON judge_competition_assignments(competition_id);
CREATE INDEX idx_judge_comp_assignments_user ON judge_competition_assignments(user_id);

-- JudgeTableAssignment: Judge assigned to specific table
CREATE TABLE judge_table_assignments (
  id UUID PRIMARY KEY,
  table_id UUID NOT NULL REFERENCES judging_tables(id),
  judge_id UUID NOT NULL REFERENCES judge_competition_assignments(id),
  judging_day_id UUID REFERENCES judging_days(id),
  is_table_lead_judge BOOLEAN NOT NULL DEFAULT false,
  scheduled_start TIMESTAMP,
  scheduled_end TIMESTAMP,
  status VARCHAR(50) NOT NULL, -- SCHEDULED, ACTIVE, COMPLETED, CANCELLED
  assigned_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_judge_table_assignments_table ON judge_table_assignments(table_id);
CREATE INDEX idx_judge_table_assignments_judge ON judge_table_assignments(judge_id);
CREATE INDEX idx_judge_table_assignments_day ON judge_table_assignments(judging_day_id);

-- ============================================================================
-- REGISTRATION & ENTRIES
-- ============================================================================

-- Registration: Created by webhook when credits purchased
CREATE TABLE registrations (
  id UUID PRIMARY KEY,
  competition_id UUID NOT NULL REFERENCES competitions(id),
  user_id UUID NOT NULL REFERENCES users(id),
  external_order_id VARCHAR(255),
  total_credits INTEGER NOT NULL,
  access_token VARCHAR(255) NOT NULL UNIQUE,
  access_token_expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_registrations_competition ON registrations(competition_id);
CREATE INDEX idx_registrations_user ON registrations(user_id);
CREATE INDEX idx_registrations_access_token ON registrations(access_token);

-- MeadEntry: A mead entered into competition
CREATE TABLE mead_entries (
  id UUID PRIMARY KEY,
  registration_id UUID NOT NULL REFERENCES registrations(id),
  category_id UUID NOT NULL REFERENCES categories(id),
  entry_code VARCHAR(50) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  honey_varieties TEXT NOT NULL,
  other_ingredients TEXT,
  status VARCHAR(50) NOT NULL, -- SUBMITTED, CHECKED_IN, JUDGING, JUDGED, DISQUALIFIED
  current_round VARCHAR(50), -- CATEGORY, MEDAL, BOS
  passed_to_medal_round BOOLEAN NOT NULL DEFAULT false,
  medal_position INTEGER,
  passed_to_bos BOOLEAN NOT NULL DEFAULT false,
  bos_position INTEGER,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_mead_entries_registration ON mead_entries(registration_id);
CREATE INDEX idx_mead_entries_category ON mead_entries(category_id);
CREATE INDEX idx_mead_entries_entry_code ON mead_entries(entry_code);
CREATE INDEX idx_mead_entries_status ON mead_entries(status);

-- Unique constraint on entry_code per competition (derived via registration)
CREATE UNIQUE INDEX idx_mead_entries_competition_code
  ON mead_entries(entry_code, registration_id);

-- EntryTableAssignment: Links entry to judging table for a round
CREATE TABLE entry_table_assignments (
  id UUID PRIMARY KEY,
  entry_id UUID NOT NULL REFERENCES mead_entries(id),
  table_id UUID NOT NULL REFERENCES judging_tables(id),
  assigned_by_id UUID REFERENCES users(id),
  round VARCHAR(50) NOT NULL, -- CATEGORY, MEDAL, BOS
  assigned_at TIMESTAMP NOT NULL,
  CONSTRAINT uq_entry_table_round UNIQUE (entry_id, round)
);

CREATE INDEX idx_entry_table_assignments_entry ON entry_table_assignments(entry_id);
CREATE INDEX idx_entry_table_assignments_table ON entry_table_assignments(table_id);

-- ============================================================================
-- SCORING
-- ============================================================================

-- Score: A score for a mead entry
CREATE TABLE scores (
  id UUID PRIMARY KEY,
  entry_id UUID NOT NULL REFERENCES mead_entries(id),
  written_by_id UUID NOT NULL REFERENCES users(id),
  total_points INTEGER NOT NULL,
  feedback TEXT,
  judge_notes TEXT,
  language VARCHAR(2) NOT NULL DEFAULT 'en',
  round VARCHAR(50) NOT NULL, -- CATEGORY, MEDAL, BOS
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_scores_entry ON scores(entry_id);
CREATE INDEX idx_scores_written_by ON scores(written_by_id);
CREATE INDEX idx_scores_round ON scores(round);

-- ScoreJudge: Links judges to scores (one in INDIVIDUAL, many in CONSENSUS)
CREATE TABLE score_judges (
  id UUID PRIMARY KEY,
  score_id UUID NOT NULL REFERENCES scores(id),
  judge_id UUID NOT NULL REFERENCES users(id),
  CONSTRAINT uq_score_judge UNIQUE (score_id, judge_id)
);

CREATE INDEX idx_score_judges_score ON score_judges(score_id);
CREATE INDEX idx_score_judges_judge ON score_judges(judge_id);

-- ScoreComponent: Individual component scores
CREATE TABLE score_components (
  id UUID PRIMARY KEY,
  score_id UUID NOT NULL REFERENCES scores(id),
  component_id UUID NOT NULL REFERENCES scoring_components(id),
  points INTEGER NOT NULL,
  notes TEXT,
  CONSTRAINT uq_score_component UNIQUE (score_id, component_id)
);

CREATE INDEX idx_score_components_score ON score_components(score_id);
CREATE INDEX idx_score_components_component ON score_components(component_id);
