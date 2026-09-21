package uz.abumme.harfgame.admin

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxHeight
import com.varabyte.kobweb.core.App
import com.varabyte.kobweb.core.init.InitKobweb
import com.varabyte.kobweb.core.init.InitKobwebContext
import com.varabyte.kobweb.silk.SilkApp
import com.varabyte.kobweb.silk.components.layout.Surface
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.style.common.SmoothColorStyle
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.palette.background
import com.varabyte.kobweb.silk.theme.colors.palette.color
import com.varabyte.kobweb.silk.theme.colors.palette.switch
import uz.abumme.harfgame.admin.components.NotFoundView
import uz.abumme.harfgame.admin.components.initTheme

@InitSilk
fun initStyles(ctx: InitSilkContext) {
    initTheme(ctx)
    // Silk's Surface paints the palette background; keep it on the panel's page colors.
    ctx.theme.palettes.light.background = Color.rgb(0xECEFF3)
    ctx.theme.palettes.light.color = Color.rgb(0x1C2330)
    ctx.theme.palettes.dark.background = Color.rgb(0x11151C)
    ctx.theme.palettes.dark.color = Color.rgb(0xE3E7ED)
    // Silk's Switch (the players list's "blocked" filter): on in the panel's primary green, off in its strong line grey.
    ctx.theme.palettes.light.switch.set(backgroundOn = Color.rgb(0x2E7449), backgroundOff = Color.rgb(0xB9C1CC), thumb = Color.rgb(0xFFFFFF))
    ctx.theme.palettes.dark.switch.set(backgroundOn = Color.rgb(0x5DAE78), backgroundOff = Color.rgb(0x3D4755), thumb = Color.rgb(0xE3E7ED))
}

@InitKobweb
fun initKobweb(ctx: InitKobwebContext) {
    AdminApp.router = ctx.router
    ctx.router.setErrorPage { NotFoundView() }
}

@App
@Composable
fun AppEntry(content: @Composable () -> Unit) {
    SilkApp {
        Surface(SmoothColorStyle.toModifier().fillMaxHeight()) {
            content()
        }
    }
}
