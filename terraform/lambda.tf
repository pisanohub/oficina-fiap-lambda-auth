locals {
  lambda_jar = "${path.module}/../target/oficina-cpf-auth.jar"
}

resource "aws_lambda_function" "cpf_auth" {
  function_name = "oficina-cpf-auth"
  description   = "Autenticacao de clientes por CPF"

  role    = data.aws_iam_role.lab_role.arn
  runtime = "java21"
  handler = "br.com.fiap.auth.CpfAuthHandler::handleRequest"

  filename         = local.lambda_jar
  source_code_hash = filebase64sha256(local.lambda_jar)

  memory_size = 512
  timeout     = 30

  vpc_config {
    subnet_ids         = var.lambda_subnet_ids
    security_group_ids = [var.lambda_security_group_id]
  }

  environment {
    variables = {
      DB_HOST                = var.db_host
      DB_PORT                = tostring(var.db_port)
      DB_NAME                = var.db_name
      DB_USERNAME            = var.db_username
      DB_PASSWORD            = var.db_password
      JWT_SECRET             = random_password.jwt.result
      JWT_EXPIRATION_SECONDS = tostring(var.jwt_expiration_seconds)
    }
  }

  tags = {
    Name     = "oficina-cpf-auth"
    Ambiente = var.environment
  }
}

resource "aws_lambda_function" "jwt_authorizer" {
  function_name = "oficina-jwt-authorizer"
  description   = "Valida o JWT de cliente antes das rotas protegidas"

  role    = data.aws_iam_role.lab_role.arn
  runtime = "java21"
  handler = "br.com.fiap.auth.JwtAuthorizerHandler::handleRequest"

  filename         = local.lambda_jar
  source_code_hash = filebase64sha256(local.lambda_jar)

  memory_size = 256
  timeout     = 10

  environment {
    variables = {
      JWT_SECRET             = random_password.jwt.result
      JWT_EXPIRATION_SECONDS = tostring(var.jwt_expiration_seconds)
    }
  }

  tags = {
    Name     = "oficina-jwt-authorizer"
    Ambiente = var.environment
  }
}

