package com.example.cad.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.cad.R

val Atkinson = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)

val Typography = Typography(
    displayMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    labelMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
)
