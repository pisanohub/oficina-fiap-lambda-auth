output "aws_account_id" {
  description = "Conta AWS que recebeu o deploy"
  value       = data.aws_caller_identity.current.account_id
}

output "lambda_name" {
  description = "Nome da Function Serverless"
  value       = aws_lambda_function.cpf_auth.function_name
}

output "api_url" {
  description = "Endpoint de autenticacao por CPF"
  value       = "${aws_api_gateway_stage.environment.invoke_url}/auth/cpf"
}

