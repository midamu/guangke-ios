package com.jianke.schedule.shared.platform

import java.time.LocalTime

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun nowHHmm(): String = LocalTime.now().toString().take(5)
