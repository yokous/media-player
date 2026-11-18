package com.example.mediaplayer.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font as GoogleFontEntry
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.mediaplayer.R

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private fun googleFamily(name: String, vararg weights: FontWeight): FontFamily = FontFamily(
    weights.map { weight ->
        GoogleFontEntry(googleFont = GoogleFont(name), fontProvider = fontProvider, weight = weight)
    }
)

private val montserrat = googleFamily("Montserrat", FontWeight.SemiBold, FontWeight.Bold)
private val inter = googleFamily("Inter", FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)

val appTypography = Typography(
    // Display
    displayLarge = TextStyle(fontFamily = montserrat, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    // Headline
    headlineMedium = TextStyle(fontFamily = montserrat, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    // Title
    titleMedium = TextStyle(fontFamily = montserrat, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    // Subtitle
    titleSmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    // Body
    bodyMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    // Body Medium
    labelLarge = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    // Label
    labelMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    // Caption
    labelSmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp)
)