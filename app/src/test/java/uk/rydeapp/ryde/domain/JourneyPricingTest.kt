package uk.rydeapp.ryde.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.JourneyPricing
import uk.rydeapp.ryde.domain.model.ServiceFeeResponsibility

class JourneyPricingTest {
    private val contribution = ContributionCalculator.calculatePence(17)

    @Test
    fun `ordinary rider-paid fee total remains unchanged`() {
        val pricing = JourneyPricing(contribution, 50, ServiceFeeResponsibility.RIDER)

        assertEquals(400, pricing.riderTotalPence)
        assertEquals(350, pricing.driverReceivesPence)
        assertFalse(pricing.hostCoversServiceFee)
    }

    @Test
    fun `host-covered fee is excluded from rider total without changing driver receipt`() {
        val riderCovered = JourneyPricing(contribution, 50, ServiceFeeResponsibility.RIDER)
        val hostCovered = JourneyPricing(contribution, 50, ServiceFeeResponsibility.HOST)

        assertEquals(350, hostCovered.riderTotalPence)
        assertEquals(riderCovered.driverReceivesPence, hostCovered.driverReceivesPence)
        assertTrue(hostCovered.hostCoversServiceFee)
    }
}
