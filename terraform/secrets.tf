resource "random_password" "jwt" {
  length  = 64
  special = false
}

