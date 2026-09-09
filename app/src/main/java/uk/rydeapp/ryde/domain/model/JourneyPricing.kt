package uk.rydeapp.ryde.domain.model

enum class ServiceFeeResponsibility {
    RIDER,
    HOST,
}

data class JourneyPricing(
    val sharedDistanceContributionPence: Int,
    val serviceFeePence: Int,
    val serviceFeeResponsibility: ServiceFeeResponsibility,
) {
    init {
        require(sharedDistanceContributionPence >= 0)
        require(serviceFeePence >= 0)
    }

    val riderTotalPence: Int
        get() = sharedDistanceContributionPence +
            if (serviceFeeResponsibility == ServiceFeeResponsibility.RIDER) serviceFeePence else 0

    val driverReceivesPence: Int get() = sharedDistanceContributionPence
    val hostCoversServiceFee: Boolean get() = serviceFeeResponsibility == ServiceFeeResponsibility.HOST
}
