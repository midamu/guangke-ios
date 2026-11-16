package com.jianke.schedule.shared.platform

import java.time.LocalDate

actual fun todayIso(): String = LocalDate.now().toString()
