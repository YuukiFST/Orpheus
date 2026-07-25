package com.yuukifst.orpheus.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class OrpheusSpacingTest {
    @Test
    fun spacingScaleMatchesSpec() {
        assertEquals(4.dp, OrpheusSpacing.xxs)
        assertEquals(8.dp, OrpheusSpacing.xs)
        assertEquals(12.dp, OrpheusSpacing.sm)
        assertEquals(16.dp, OrpheusSpacing.md)
        assertEquals(24.dp, OrpheusSpacing.lg)
        assertEquals(32.dp, OrpheusSpacing.xl)
    }
}
