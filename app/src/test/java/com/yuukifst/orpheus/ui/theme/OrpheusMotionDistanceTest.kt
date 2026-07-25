package com.yuukifst.orpheus.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class OrpheusMotionDistanceTest {
    @Test
    fun distanceTokensMatchTransitionsDevScale() {
        assertEquals(4.dp, OrpheusMotion.DistanceMicro)
        assertEquals(6.dp, OrpheusMotion.DistanceSmall)
        assertEquals(8.dp, OrpheusMotion.DistanceBase)
        assertEquals(12.dp, OrpheusMotion.DistanceMedium)
        assertEquals(30.dp, OrpheusMotion.DistanceLarge)
    }
}
