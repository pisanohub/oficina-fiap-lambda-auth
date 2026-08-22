resource "aws_api_gateway_rest_api" "oficina" {
  name        = "oficina-api"
  description = "API Gateway da Oficina FIAP"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_api_gateway_resource" "auth" {
  rest_api_id = aws_api_gateway_rest_api.oficina.id
  parent_id   = aws_api_gateway_rest_api.oficina.root_resource_id
  path_part   = "auth"
}

resource "aws_api_gateway_resource" "cpf" {
  rest_api_id = aws_api_gateway_rest_api.oficina.id
  parent_id   = aws_api_gateway_resource.auth.id
  path_part   = "cpf"
}

resource "aws_api_gateway_method" "cpf_post" {
  rest_api_id   = aws_api_gateway_rest_api.oficina.id
  resource_id   = aws_api_gateway_resource.cpf.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "cpf_lambda" {
  rest_api_id = aws_api_gateway_rest_api.oficina.id
  resource_id = aws_api_gateway_resource.cpf.id
  http_method = aws_api_gateway_method.cpf_post.http_method

  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.cpf_auth.invoke_arn
}

resource "aws_lambda_permission" "api_gateway_cpf" {
  statement_id  = "AllowApiGatewayInvokeCpfAuth"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.cpf_auth.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.oficina.execution_arn}/*/*"
}

resource "aws_api_gateway_authorizer" "cliente_jwt" {
  name                             = "oficina-cliente-jwt"
  rest_api_id                      = aws_api_gateway_rest_api.oficina.id
  authorizer_uri                   = aws_lambda_function.jwt_authorizer.invoke_arn
  authorizer_result_ttl_in_seconds = 300
  identity_source                  = "method.request.header.Authorization"
  identity_validation_expression   = "^Bearer [-0-9A-Za-z._~+/]+=*$"
  type                             = "TOKEN"
}

resource "aws_lambda_permission" "api_gateway_authorizer" {
  statement_id  = "AllowApiGatewayInvokeJwtAuthorizer"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.jwt_authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.oficina.execution_arn}/authorizers/${aws_api_gateway_authorizer.cliente_jwt.id}"
}

resource "aws_api_gateway_resource" "api" {
  rest_api_id = aws_api_gateway_rest_api.oficina.id
  parent_id   = aws_api_gateway_rest_api.oficina.root_resource_id
  path_part   = "api"
}

resource "aws_api_gateway_resource" "api_proxy" {
  rest_api_id = aws_api_gateway_rest_api.oficina.id
  parent_id   = aws_api_gateway_resource.api.id
  path_part   = "{proxy+}"
}

resource "aws_api_gateway_method" "protected_proxy" {
  rest_api_id   = aws_api_gateway_rest_api.oficina.id
  resource_id   = aws_api_gateway_resource.api_proxy.id
  http_method   = "ANY"
  authorization = "CUSTOM"
  authorizer_id = aws_api_gateway_authorizer.cliente_jwt.id

  request_parameters = {
    "method.request.path.proxy" = true
  }
}

resource "aws_api_gateway_integration" "protected_app" {
  rest_api_id = aws_api_gateway_rest_api.oficina.id
  resource_id = aws_api_gateway_resource.api_proxy.id
  http_method = aws_api_gateway_method.protected_proxy.http_method

  integration_http_method = "ANY"
  type                    = "HTTP_PROXY"
  uri                     = "${trimsuffix(var.app_base_url, "/")}/api/{proxy}"

  request_parameters = {
    "integration.request.path.proxy" = "method.request.path.proxy"
  }
}

resource "aws_api_gateway_deployment" "oficina" {
  rest_api_id = aws_api_gateway_rest_api.oficina.id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_resource.auth.id,
      aws_api_gateway_resource.cpf.id,
      aws_api_gateway_method.cpf_post.id,
      aws_api_gateway_integration.cpf_lambda.id,
      aws_api_gateway_authorizer.cliente_jwt.id,
      aws_api_gateway_resource.api.id,
      aws_api_gateway_resource.api_proxy.id,
      aws_api_gateway_method.protected_proxy.id,
      aws_api_gateway_integration.protected_app.id
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [
    aws_api_gateway_integration.cpf_lambda,
    aws_api_gateway_integration.protected_app,
    aws_lambda_permission.api_gateway_authorizer
  ]
}

resource "aws_api_gateway_stage" "environment" {
  rest_api_id   = aws_api_gateway_rest_api.oficina.id
  deployment_id = aws_api_gateway_deployment.oficina.id
  stage_name    = var.environment
}

