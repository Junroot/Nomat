package ilpak.nomat.infrastructure.validator

import jakarta.validation.Validation

object HibernateValidator {
    val default = Validation.buildDefaultValidatorFactory().validator
}
