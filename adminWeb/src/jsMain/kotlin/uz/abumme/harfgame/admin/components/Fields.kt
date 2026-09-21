package uz.abumme.harfgame.admin.components

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.attributes.InputType
import androidx.compose.web.attributes.SelectAttrsScope
import org.jetbrains.compose.web.attributes.builders.InputAttrsScope
import org.jetbrains.compose.web.attributes.builders.TextAreaAttrsScope
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.name
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import uz.abumme.harfgame.admin.Strings

val FieldStyle = CssStyle.base {
    Modifier.css("display" to "flex", "flex-direction" to "column", "min-width" to "0")
}

/** A validation message under a field; renders nothing without a message. Announced to screen readers. */
@Composable
fun FieldError(message: String?, id: String? = null) {
    if (message.isNullOrEmpty()) return
    P(FieldErrorStyle.toModifier().toAttrs {
        attr("role", "alert")
        id?.let { id(it) }
    }) { Text(message) }
}

/** The message for [field]'s reason in [errors], or null. */
fun errorFor(errors: Map<String, String>, field: String): String? = errors[field]?.let { Strings.fieldReason(field, it) }

/**
 * A labelled text or password input with hint and error. [name] and [autoComplete] let browsers and password
 * managers fill it (`username`, `current-password`, `new-password`).
 */
@Composable
fun TextField(
    id: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    hint: String? = null,
    password: Boolean = false,
    name: String = id,
    autoComplete: String? = null,
    enabled: Boolean = true,
    inputMode: String? = null,
    modifier: Modifier = Modifier,
) {
    Div(FieldStyle.toModifier().then(modifier).toAttrs()) {
        Label(forId = id, attrs = FieldLabelStyle.toModifier().toAttrs()) { Text(label) }
        Input(
            if (password) InputType.Password else InputType.Text,
            attrs = TextInputStyle.toModifier().toAttrs<InputAttrsScope<String>> {
                id(id)
                name(name)
                value(value)
                onInput { onValueChange(it.value) }
                autoComplete?.let { attr("autocomplete", it) }
                inputMode?.let { attr("inputmode", it) }
                attr("spellcheck", "false")
                attr("autocapitalize", "off")
                if (!enabled) disabled()
                if (error != null) attr("aria-invalid", "true")
                val describedBy = listOfNotNull(hint?.let { "$id-hint" }, error?.let { "$id-error" })
                if (describedBy.isNotEmpty()) attr("aria-describedby", describedBy.joinToString(" "))
            },
        )
        if (hint != null) P(FieldHintStyle.toModifier().toAttrs { id("$id-hint") }) { Text(hint) }
        FieldError(error, "$id-error")
    }
}

/**
 * A hidden username field for forms that only ask for passwords, so password managers know which account a new or
 * current password belongs to.
 */
@Composable
fun HiddenUsernameField(username: String) {
    Input(InputType.Text, attrs = Modifier.toAttrs<InputAttrsScope<String>> {
        value(username)
        attr("autocomplete", "username")
        attr("hidden", "")
        attr("readonly", "")
        attr("tabindex", "-1")
        attr("aria-hidden", "true")
    })
}

/** A labelled multi-line text input with hint and error, e.g. a pasted word list. */
@Composable
fun TextAreaField(
    id: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    error: String? = null,
    rows: Int = 10,
    enabled: Boolean = true,
) {
    Div(FieldStyle.toModifier().toAttrs()) {
        Label(forId = id, attrs = FieldLabelStyle.toModifier().toAttrs()) { Text(label) }
        TextArea(
            value = value,
            attrs = TextInputStyle.toModifier()
                .css("height" to "auto", "padding" to "10px 12px", "resize" to "vertical", "line-height" to "1.5")
                .toAttrs<TextAreaAttrsScope> {
                    id(id)
                    attr("rows", rows.toString())
                    attr("spellcheck", "false")
                    attr("autocapitalize", "off")
                    onInput { onValueChange(it.value) }
                    if (!enabled) disabled()
                    if (error != null) attr("aria-invalid", "true")
                    val describedBy = listOfNotNull(hint?.let { "$id-hint" }, error?.let { "$id-error" })
                    if (describedBy.isNotEmpty()) attr("aria-describedby", describedBy.joinToString(" "))
                },
        )
        if (hint != null) P(FieldHintStyle.toModifier().toAttrs { id("$id-hint") }) { Text(hint) }
        FieldError(error, "$id-error")
    }
}

/** A labelled native date input; [value] is `yyyy-mm-dd` or empty. */
@Composable
fun DateField(id: String, label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Div(FieldStyle.toModifier().then(modifier).toAttrs()) {
        Label(forId = id, attrs = FieldLabelStyle.toModifier().toAttrs()) { Text(label) }
        Input(
            InputType.Date,
            attrs = TextInputStyle.toModifier().toAttrs<InputAttrsScope<String>> {
                id(id)
                value(value)
                onInput { onValueChange(it.value) }
            },
        )
    }
}

/** A labelled native `<select>`: Silk has no select widget, and the native one works with every input method. */
@Composable
fun SelectField(
    id: String,
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    error: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Div(FieldStyle.toModifier().then(modifier).toAttrs()) {
        Label(forId = id, attrs = FieldLabelStyle.toModifier().toAttrs()) { Text(label) }
        Select(
            attrs = TextInputStyle.toModifier().css("padding-right" to "8px").toAttrs<SelectAttrsScope> {
                id(id)
                onChange { event -> event.value?.let(onValueChange) }
                if (!enabled) disabled()
                if (error != null) attr("aria-invalid", "true")
            },
        ) {
            options.forEach { (optionValue, optionLabel) ->
                Option(optionValue, { if (optionValue == value) selected() }) { Text(optionLabel) }
            }
        }
        FieldError(error, "$id-error")
    }
}
