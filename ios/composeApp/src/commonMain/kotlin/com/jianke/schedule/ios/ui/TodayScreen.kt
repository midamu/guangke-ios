package com.jianke.schedule.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.model.courseTimeRange
import com.jianke.schedule.shared.parse.ScheduleWeek
import com.jianke.schedule.shared.platform.nowHHmm
import com.jianke.schedule.shared.platform.todayIso
import com.jianke.schedule.shared.util.IsoDate

private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 今日课程：按节次排，标注「已结束 / 进行中 / 未开始」。 */
@Composable
fun TodayScreen(schedule: Schedule) {
    val palette = LocalPalette.current
    val iso = todayIso()
    val weekday = IsoDate.weekdayIndex(iso)
    val week = ScheduleWeek.currentWeekOf(schedule, iso)
    val now = nowHHmm()

    val courses = schedule.coursesOfWeek(week)
        .filter { it.dayOfWeek == weekday }
        .sortedBy { it.startPeriod }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(text = "$iso ${WEEKDAY_NAMES[weekday - 1]}", style = Type.caption, color = palette.subText)
        Spacer(Modifier.height(2.dp))
        Text(text = "第 $week 周", style = Type.title)
        Spacer(Modifier.height(14.dp))

        if (courses.isEmpty()) {
            Card {
                Text(text = "今天没有课", style = Type.body)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "课表存在本机，不会因为没更新就消失。",
                    style = Type.caption,
                    color = palette.subText,
                )
            }
            return@Column
        }

        courses.forEach { c ->
            // 用大节口径：1-2 节 = 08:20-10:00（不是 08:20-09:55）
            val (start, end) = courseTimeRange(c.startPeriod, c.endPeriod)
            val state = when {
                now >= end -> "已结束"
                now >= start -> "进行中"
                else -> "未开始"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (c.color != 0) Color(c.color) else palette.primary),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = c.name, style = Type.headline, maxLines = 2)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = listOf(start, end).filter { it.isNotEmpty() }.joinToString("-") +
                            "  " + c.periodText + " 节  " + c.room,
                        style = Type.caption,
                        color = palette.subText,
                    )
                }
                Spacer(Modifier.width(8.dp))
                ArrangementRow(state = state)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ArrangementRow(state: String) {
    val palette = LocalPalette.current
    val color = when (state) {
        "进行中" -> palette.primary
        "已结束" -> palette.subText
        else -> palette.primary.copy(alpha = 0.6f)
    }
    Text(text = state, style = Type.tiny, color = color)
}
