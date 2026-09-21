package uz.abumme.harfgame.admin.pages.staff

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.style.until
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.builders.InputAttrsScope
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Fieldset
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Legend
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.components.FieldError
import uz.abumme.harfgame.admin.components.FieldHintStyle
import uz.abumme.harfgame.admin.components.FieldLabelStyle
import uz.abumme.harfgame.admin.components.SelectField
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.components.errorFor
import uz.abumme.harfgame.admin.forms.FieldErrors
import uz.abumme.harfgame.admin.forms.StaffFormState
import uz.abumme.harfgame.data.admin.Role

val FormGridStyle = CssStyle {
    base { Modifier.css("display" to "grid", "grid-template-columns" to "repeat(2, minmax(0, 1fr))", "gap" to "18px 20px") }
    until(Breakpoint.SM) { Modifier.css("grid-template-columns" to "minmax(0, 1fr)") }
}

val LanguageChoiceStyle = CssStyle.base {
    Modifier.css(
        "display" to "inline-flex",
        "align-items" to "center",
        "gap" to "8px",
        "padding" to "7px 12px",
        "border" to "1px solid ${Tokens.LINE_STRONG}",
        "border-radius" to "6px",
        "cursor" to "pointer",
        "font-size" to "14px",
        "background-color" to Tokens.SURFACE,
    )
}

/**
 * The fields shared by the create and edit pages. [creating] adds the username and password; [languages] are the
 * pack languages the server offers; [errors] hold client or server reasons per field.
 */
@Composable
fun StaffFields(
    state: StaffFormState,
    onChange: (StaffFormState) -> Unit,
    languages: List<String>,
    errors: FieldErrors,
    creating: Boolean,
    enabled: Boolean,
) {
    Div(FormGridStyle.toModifier().toAttrs()) {
        if (creating) {
            TextField(
                id = "username",
                label = Strings.Staff.USERNAME,
                value = state.username,
                onValueChange = { onChange(state.copy(username = it)) },
                error = errorFor(errors, "username"),
                hint = Strings.Staff.USERNAME_HINT,
                autoComplete = "off",
                enabled = enabled,
            )
            TextField(
                id = "password",
                label = Strings.Staff.PASSWORD,
                value = state.password,
                onValueChange = { onChange(state.copy(password = it)) },
                error = errorFor(errors, "password"),
                hint = Strings.Staff.PASSWORD_HINT,
                password = true,
                autoComplete = "new-password",
                enabled = enabled,
            )
        }
        TextField(
            id = "displayName",
            label = Strings.Staff.DISPLAY_NAME,
            value = state.displayName,
            onValueChange = { onChange(state.copy(displayName = it)) },
            error = errorFor(errors, "displayName"),
            hint = Strings.Staff.DISPLAY_NAME_HINT,
            autoComplete = "off",
            enabled = enabled,
        )
        TextField(
            id = "telegramUserId",
            label = Strings.Staff.TELEGRAM,
            value = state.telegramUserId,
            onValueChange = { onChange(state.copy(telegramUserId = it)) },
            error = errorFor(errors, "telegramUserId"),
            hint = Strings.Staff.TELEGRAM_HINT,
            autoComplete = "off",
            inputMode = "numeric",
            enabled = enabled,
        )
        SelectField(
            id = "role",
            label = Strings.Staff.ROLE,
            value = state.role.name,
            options = Role.entries.map { it.name to Strings.Roles.label(it) },
            onValueChange = { value -> onChange(state.copy(role = Role.valueOf(value))) },
            error = errorFor(errors, "role"),
            enabled = enabled,
        )
    }
    LanguagePicker(state, onChange, languages, errors, enabled)
}

@Composable
private fun LanguagePicker(
    state: StaffFormState,
    onChange: (StaffFormState) -> Unit,
    languages: List<String>,
    errors: FieldErrors,
    enabled: Boolean,
) {
    Fieldset(Modifier.css("border" to "none", "padding" to "0", "margin" to "18px 0 0", "min-width" to "0").toAttrs {
        if (errors.containsKey("languages")) attr("aria-invalid", "true")
        attr("aria-describedby", "languages-hint")
    }) {
        Legend(FieldLabelStyle.toModifier().css("padding" to "0").toAttrs()) { Text(Strings.Staff.LANGUAGES) }
        Div(Modifier.css("display" to "flex", "flex-wrap" to "wrap", "gap" to "8px").toAttrs()) {
            languages.forEach { lang ->
                val checked = lang in state.languages
                Label(forId = "lang-$lang", attrs = LanguageChoiceStyle.toModifier()
                    .css("border-color" to if (checked) Tokens.CORRECT else Tokens.LINE_STRONG)
                    .toAttrs()) {
                    Input(InputType.Checkbox, attrs = Modifier.css("accent-color" to Tokens.CORRECT, "margin" to "0").toAttrs<InputAttrsScope<Boolean>> {
                        id("lang-$lang")
                        checked(checked)
                        if (!enabled) disabled()
                        onChange { event ->
                            val selected = if (event.value) state.languages + lang else state.languages - lang
                            onChange(state.copy(languages = selected))
                        }
                    })
                    Text(Strings.Languages.label(lang))
                }
            }
        }
        P(FieldHintStyle.toModifier().toAttrs { id("languages-hint") }) { Text(Strings.Staff.LANGUAGES_HINT) }
        FieldError(errorFor(errors, "languages"), "languages-error")
    }
}
