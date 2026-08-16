package com.v2ray.ang.ui.fandogh

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/** The three first-run steps, in order. */
enum class OnboardingStep { Subscription, VpnPermission, Notifications }

/**
 * First-run setup.
 *
 * The order matters: the subscription comes first because without it every later screen
 * has nothing to act on, and the two permission prompts follow because Android will only
 * show its own dialogs in response to a deliberate tap. Both permission steps are
 * skippable — neither is required to reach the app, and a forced prompt is the fastest
 * way to get a permanent denial.
 */
@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    subscriptionUrl: String,
    busy: Boolean,
    message: String?,
    onUrlChange: (String) -> Unit,
    onSubmitSubscription: () -> Unit,
    onSkipSubscription: () -> Unit,
    onGrantVpn: () -> Unit,
    onGrantNotifications: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FandoghSpace.xl)
    ) {
        Spacer(Modifier.height(FandoghSpace.xl))

        // The welcome step leads with the mark; later steps lead with progress.
        if (step == OnboardingStep.Subscription) {
            Spacer(Modifier.height(FandoghSpace.xxl))
            HaloMark { FandoghLogo(Modifier.size(76.dp)) }
            Spacer(Modifier.height(FandoghSpace.xxl))
            Text(
                text = stringResource(R.string.fandogh_welcome_title),
                color = FandoghColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(FandoghSpace.sm))
            Text(
                text = stringResource(R.string.fandogh_welcome_subtitle),
                color = FandoghColors.TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(FandoghSpace.xxl))
        } else {
            StepProgress(step)
            Spacer(Modifier.height(FandoghSpace.xxl))
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally { -it / 3 } + fadeOut(tween(180)))
            },
            label = "step"
        ) { current ->
            when (current) {
                OnboardingStep.Subscription -> SubscriptionStep(
                    url = subscriptionUrl,
                    busy = busy,
                    message = message,
                    onUrlChange = onUrlChange,
                    onSubmit = onSubmitSubscription,
                    onSkip = onSkipSubscription
                )

                OnboardingStep.VpnPermission -> PermissionStep(
                    glyph = { KeyGlyph(Modifier.size(38.dp)) },
                    title = stringResource(R.string.fandogh_onboard_vpn_title),
                    body = stringResource(R.string.fandogh_onboard_vpn_body),
                    action = stringResource(R.string.fandogh_onboard_continue),
                    onAction = onGrantVpn,
                    onSkip = onSkip
                )

                OnboardingStep.Notifications -> PermissionStep(
                    glyph = { BellGlyph(Modifier.size(36.dp)) },
                    title = stringResource(R.string.fandogh_onboard_notify_title),
                    body = stringResource(R.string.fandogh_onboard_notify_body),
                    action = stringResource(R.string.fandogh_onboard_allow),
                    onAction = onGrantNotifications,
                    onSkip = onSkip
                )
            }
        }

        Spacer(Modifier.height(FandoghSpace.xxl))
    }
}

/** Logo inside two rings, one of which slowly breathes — the reference's welcome mark. */
@Composable
private fun HaloMark(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "halo")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "haloBreathe"
    )

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                drawCircle(
                    color = FandoghColors.AccentGreen.copy(alpha = 0.30f + 0.25f * breathe),
                    radius = size.minDimension / 2 - 1.dp.toPx(),
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = size.minDimension * 0.36f,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            content()
        }
    }
}

/** Three segments; filled ones mark progress, matching the reference's header. */
@Composable
private fun StepProgress(step: OnboardingStep) {
    val reached = OnboardingStep.entries.indexOf(step)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FandoghSpace.md)
    ) {
        OnboardingStep.entries.forEachIndexed { index, _ ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(FandoghRadius.pill))
                    .background(
                        if (index <= reached) FandoghColors.AccentGreen
                        else Color.White.copy(alpha = 0.12f)
                    )
            )
        }
    }
}

@Composable
private fun SubscriptionStep(
    url: String,
    busy: Boolean,
    message: String?,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit
) {
    Column {
        GlassCard(contentPadding = PaddingValues(FandoghSpace.xl)) {
            Text(
                text = stringResource(R.string.fandogh_onboard_sub_body),
                color = FandoghColors.TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(FandoghSpace.xl))
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                placeholder = {
                    Text(
                        "https://panel.example.com/sub/xxxx",
                        color = FandoghColors.TextTertiary,
                        fontSize = 14.sp
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(FandoghRadius.tile),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FandoghColors.TextPrimary,
                    unfocusedTextColor = FandoghColors.TextPrimary,
                    focusedBorderColor = FandoghColors.AccentBlue,
                    unfocusedBorderColor = FandoghColors.Border,
                    cursorColor = FandoghColors.AccentBlueBright,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(FandoghSpace.xl))
            if (busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = FandoghColors.AccentBlueBright,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(FandoghSpace.md))
                        Text(
                            stringResource(R.string.fandogh_updating),
                            color = FandoghColors.TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                GradientButton(
                    text = stringResource(R.string.fandogh_onboard_continue),
                    onClick = onSubmit
                )
            }

            if (message != null) {
                Spacer(Modifier.height(FandoghSpace.md))
                Text(
                    text = message,
                    color = FandoghColors.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(FandoghSpace.lg))
        SkipRow(stringResource(R.string.fandogh_onboard_later), onSkip)
    }
}

@Composable
private fun PermissionStep(
    glyph: @Composable () -> Unit,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    onSkip: () -> Unit
) {
    Column {
        GlassCard(contentPadding = PaddingValues(FandoghSpace.xl)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(46.dp))
                        .border(
                            BorderStroke(1.5.dp, FandoghColors.AccentGreen.copy(alpha = 0.45f)),
                            RoundedCornerShape(46.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) { glyph() }
            }

            Spacer(Modifier.height(FandoghSpace.xl))
            Text(
                text = title,
                color = FandoghColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(FandoghSpace.md))
            Text(
                text = body,
                color = FandoghColors.TextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(FandoghSpace.xl))
        GradientButton(text = action, onClick = onAction)
        Spacer(Modifier.height(FandoghSpace.lg))
        SkipRow(stringResource(R.string.fandogh_onboard_skip), onSkip)
    }
}

@Composable
private fun SkipRow(label: String, onSkip: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = FandoghColors.TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(FandoghRadius.pill))
                .clickable(onClick = onSkip)
                .padding(horizontal = FandoghSpace.xxl, vertical = FandoghSpace.md)
        )
    }
}

@Composable
private fun KeyGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = FandoghColors.AccentBlueBright
        val r = size.minDimension * 0.20f
        val c = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.5f)
        drawCircle(color, radius = r, center = c, style = Stroke(width = 3.dp.toPx()))
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(c.x + r, c.y),
            androidx.compose.ui.geometry.Offset(size.width * 0.92f, c.y),
            strokeWidth = 3.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * 0.78f, c.y),
            androidx.compose.ui.geometry.Offset(size.width * 0.78f, c.y + size.height * 0.16f),
            strokeWidth = 3.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
private fun BellGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = FandoghColors.AccentBlueBright
        val w = size.width
        val h = size.height
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.10f)
            cubicTo(w * 0.78f, h * 0.10f, w * 0.78f, h * 0.38f, w * 0.80f, h * 0.62f)
            lineTo(w * 0.20f, h * 0.62f)
            cubicTo(w * 0.22f, h * 0.38f, w * 0.22f, h * 0.10f, w * 0.5f, h * 0.10f)
            close()
        }
        drawPath(body, color)
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.62f),
            androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.74f),
            strokeWidth = 3.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.38f, h * 0.72f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.18f)
        )
    }
}
