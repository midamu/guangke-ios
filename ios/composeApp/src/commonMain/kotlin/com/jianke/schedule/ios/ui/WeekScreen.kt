package com.jianke.schedule.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.model.layoutDay
import com.jianke.schedule.shared.model.schoolBigPeriods
import com.jianke.schedule.shared.parse.ScheduleWeek
import com.jianke.schedule.shared.platform.todayIso

private val ROW_HEIGHT = 64.dp
private val HEADER_HEIGHT = 42.dp
private val GUTTER_WIDTH = 26.dp
private val WEEK_DAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 周课表：左边节次栏，右边 7 天；同一天的课按 [layoutDay] 的车道并排。 */
@Composable
fun WeekScreen(schedule: Schedule) {
    val totalWeeks = maxOf(1, schedule.semester.totalWeeks)
    var week by remember(schedule) {
        mutableIntStateOf(ScheduleWeek.currentWeekOf(schedule, todayIso()).coerceIn(1, totalWeeks))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekNavBar(week = week, total = totalWeeks, onPrev = {
            if (week > 1) week -= 1
        }, onNext = {
            if (week < totalWeeks) week += 1
        })
        val dates = ScheduleWeek.weekDatesOf(schedule.semester.startDate, week)
        val courses = schedule.coursesOfWeek(week)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
        ) {
            // 节次栏
            Column(modifier = Modifier.width(GUTTER_WIDTH)) {
                Spacer(Modifier.height(HEADER_HEIGHT))
                schoolBigPeriods.forEach { p ->
                    Box(
                        modifier = Modifier.height(ROW_HEIGHT),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text(text = "${p.startPeriod}-${p.endPeriod}", style = Type.tiny, color = LocalPalette.current.subText)
                    }
                }
            }

            for (day in 1..7) {
                Column(modifier = Modifier.weight(1f)) {
                    DayHeader(index = day - 1, date = dates.getOrNull(day - 1))
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT * schoolBigPeriods.size),
                    ) {
                        val cellWidth: Dp = maxWidth
                        GridLines()
                        val placed = layoutDay(courses.filter { it.dayOfWeek == day })
                        placed.forEach { p ->
                            val color = if (p.course.color != 0) Color(p.course.color) else LocalPalette.current.primary
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = cellWidth * p.lane / p.lanes,
                                        y = ROW_HEIGHT * p.startRow,
                                    )
                                    .width(cellWidth / p.lanes)
                                    .height(ROW_HEIGHT * (p.endRow - p.startRow + 1))
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color)
                                    .padding(horizontal = 3.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = p.course.name,
                                    style = Type.tiny,
                                    color = Color.White,
                                    maxLines = 4,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekNavBar(week: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            style = Type.title,
            color = if (week > 1) palette.primary else palette.divider,
            modifier = Modifier.clickable(enabled = week > 1, onClick = onPrev).padding(horizontal = 10.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(text = "第 $week 周 / 共 $total 周", style = Type.headline, align = TextAlign.Center)
        }
        Text(
            text = "›",
            style = Type.title,
            color = if (week < total) palette.primary else palette.divider,
            modifier = Modifier.clickable(enabled = week < total, onClick = onNext).padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun DayHeader(index: Int, date: String?) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier.height(HEADER_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = WEEK_DAYS[index], style = Type.tiny, color = palette.subText)
        Text(
            text = date?.substringAfterLast('-')?.trimStart('0') ?: "",
            style = Type.tiny,
            color = palette.subText.copy(alpha = 0.7f),
        )
    }
}

/** 每个大节顶部一条细线：让空格子也能看出节次边界。 */
@Composable
private fun GridLines() {
    val palette = LocalPalette.current
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(schoolBigPeriods.size) {
            Box(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .align(Alignment.TopCenter)
                        .background(palette.divider),
                )
            }
        }
    }
}
