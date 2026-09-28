package com.metrolist.music.car.ui.component

import com.metrolist.music.car.feedback.CarOperationProgress
import com.metrolist.music.car.feedback.CarOperationStage
import org.junit.Assert.assertEquals
import org.junit.Test

class CarOperationPanelFormattingTest {
    @Test
    fun detailsRenderConcreteProgressValuesInsteadOfTemplateText() {
        val progress =
            CarOperationProgress(
                stage = CarOperationStage.IMPORTING,
                completedItems = 3,
                totalItems = 10,
                bytesProcessed = 1_024,
                totalBytes = 2_048,
                bytesPerSecond = 512,
            )

        assertEquals(
            "3/10 items · 1.0 KB / 2.0 KB · 50% · 512 B/s",
            formatCarOperationDetails(progress),
        )
    }
}
