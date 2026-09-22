package com.echosense.echo

import com.echosense.echo.core.fusion.DistanceStateMachine
import com.echosense.echo.core.fusion.ObstacleState
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceStateMachineTest {

    @Test
    fun testHysteresisPreventsFlappingAtBoundary() {
        val machine = DistanceStateMachine(hysteresisBandMeters = 0.05f)

        // Start at 1.5m -> CAUTION
        assertEquals(ObstacleState.CAUTION, machine.update(1.5f))

        // Approach 1.02m -> still CAUTION
        assertEquals(ObstacleState.CAUTION, machine.update(1.02f))

        // Drop slightly below 1.05m threshold to 1.01m -> should STAY CAUTION due to 0.05m hysteresis!
        // Threshold is 1.05m - 0.05m = 1.00m to transition into CLOSE from CAUTION
        assertEquals(ObstacleState.CAUTION, machine.update(1.01f))

        // Drop below 1.00m to 0.98m -> now transitions to CLOSE
        assertEquals(ObstacleState.CLOSE, machine.update(0.98f))

        // Flap up slightly to 1.02m -> should STAY CLOSE due to 1.05m + 0.05m = 1.10m hysteresis!
        assertEquals(ObstacleState.CLOSE, machine.update(1.02f))

        // Move away past 1.12m -> now transitions back to CAUTION
        assertEquals(ObstacleState.CAUTION, machine.update(1.12f))
    }

    @Test
    fun testCriticalDistanceTransition() {
        val machine = DistanceStateMachine(hysteresisBandMeters = 0.05f)

        assertEquals(ObstacleState.CRITICAL, machine.update(0.25f))
        // Must exceed 0.35m + 0.05m = 0.40m to exit CRITICAL
        assertEquals(ObstacleState.CRITICAL, machine.update(0.38f))
        assertEquals(ObstacleState.VERY_CLOSE, machine.update(0.42f))
    }
}
