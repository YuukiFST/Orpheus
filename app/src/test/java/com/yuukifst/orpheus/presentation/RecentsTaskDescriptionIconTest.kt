package com.yuukifst.orpheus.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecentsTaskDescriptionIconTest {

    @Test
    fun `api 33 plus must use resource id not bitmap Icon`() {
        // TaskDescription.Builder.setIcon(Icon) throws unless TYPE_RESOURCE.
        assertEquals(
            RecentsTaskDescriptionIcon.ResourceId,
            chooseRecentsTaskDescriptionIcon(sdkInt = 33),
        )
        assertEquals(
            RecentsTaskDescriptionIcon.ResourceId,
            chooseRecentsTaskDescriptionIcon(sdkInt = 34),
        )
    }

    @Test
    fun `pre tiramisu uses decoded bitmap constructor`() {
        assertEquals(
            RecentsTaskDescriptionIcon.DecodedBitmap,
            chooseRecentsTaskDescriptionIcon(sdkInt = 32),
        )
        assertEquals(
            RecentsTaskDescriptionIcon.DecodedBitmap,
            chooseRecentsTaskDescriptionIcon(sdkInt = 28),
        )
    }
}
