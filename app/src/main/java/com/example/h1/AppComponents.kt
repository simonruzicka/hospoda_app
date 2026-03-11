package com.example.h1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign


@Composable
fun HeavyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true // Výchozí hodnota je true (zapnuto)
) {
    Button(
        onClick = onClick,
        enabled = enabled, // TADY chybělo předání stavu samotnému tlačítku
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE5D5C1), // Tvoje béžová
            contentColor = Color.Black, // Černý text
            disabledContainerColor = Color(0xFFE5D5C1).copy(alpha = 0.5f), // Poloprůhledná béžová, když je vypnuto
            disabledContentColor = Color.Black.copy(alpha = 0.5f) // Poloprůhledný text, když je vypnuto
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .border(width = 3.dp, color = Color.Black, shape = RoundedCornerShape(12.dp))
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            // fontFamily = MyCustomFont
        )
    }
}

@Composable
fun HeavyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color(0xFFE5D5C1))
            .border(width = 3.dp, color = Color.Black)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Color.Black.copy(alpha = 0.6f),
                fontSize = 20.sp
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 20.sp
            ),
            cursorBrush = SolidColor(Color.Black),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BigAddCard(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color(0xFFE5D5C1), shape = RoundedCornerShape(24.dp))
            .border(3.dp, Color.Black, shape = RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center // Vycentruje Column uprostřed Boxu
    ) {
        Column(
            // Vycentruje prvky uvnitř Column horizontálně na střed
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "+",
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                // KLÍČOVÉ PRO CENTROVÁNÍ VÍCEŘÁDKOVÉHO TEXTU:
                textAlign = TextAlign.Center
            )
        }
    }
}