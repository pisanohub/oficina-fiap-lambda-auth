variable "aws_region" {
  description = "Regiao AWS utilizada no deploy"
  type        = string
  default     = "us-east-1"
}

variable "aws_profile" {
  description = "Perfil AWS local; deve permanecer nulo no GitHub Actions"
  type        = string
  default     = null
  nullable    = true
}

variable "lab_role_name" {
  description = "Role preexistente disponibilizada pelo AWS Academy Learner Lab"
  type        = string
  default     = "LabRole"
}

variable "lambda_subnet_ids" {
  description = "Subnets privadas ou do laboratorio usadas pela Lambda"
  type        = list(string)

  validation {
    condition     = length(var.lambda_subnet_ids) >= 2
    error_message = "Informe pelo menos duas subnets para a Lambda."
  }
}

variable "lambda_security_group_id" {
  description = "Security Group com acesso ao PostgreSQL, fornecido pela infraestrutura de banco/rede"
  type        = string
}

variable "db_host" {
  description = "Endpoint privado do PostgreSQL/RDS"
  type        = string
}

variable "db_port" {
  description = "Porta do PostgreSQL"
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "Nome do banco de dados"
  type        = string
  default     = "oficina"
}

variable "db_username" {
  description = "Usuario de acesso ao banco"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Senha de acesso ao banco"
  type        = string
  sensitive   = true
}

variable "jwt_expiration_seconds" {
  description = "Validade do JWT em segundos"
  type        = number
  default     = 3600

  validation {
    condition     = var.jwt_expiration_seconds > 0
    error_message = "A validade do JWT deve ser maior que zero."
  }
}

variable "environment" {
  description = "Ambiente implantado"
  type        = string
  default     = "dev"
}

