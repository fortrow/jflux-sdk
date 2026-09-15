# ObjectiveConsole

`com.fortrow.jflux.console.ObjectiveConsole` is the JSON-focused terminal
writer/editor. The console package keeps this as its primary public UI; the
old `com.fortrow.jflux.console.ProgressTaskList` name is only a deprecated
compatibility facade for the stdout implementation.

## Basic usage

```java
ObjectiveConsole editor = ObjectiveConsole.create("{\"name\":\"Ada\"}");
String json = editor.edit();
```

The editor keeps the parsed document structured and owns the formatting. Its
view displays quoted keys and strings, commas, indentation, braces, and
brackets. `render()` includes selection highlighting when ANSI output is
enabled; `renderPlain()` is useful for logs, tests, and redirected output.
`json()` returns the compact JSON-Simple representation.

Non-JSON constructor input creates an empty object for compatibility with the
original command-only placeholder. Use valid JSON when an initial document is
required.

## Controls

- Up/Down selects the previous or next key/value/container.
- Left/Right moves within the selected key or value; Home/End moves to its edge.
- Backspace/Delete edit the selected text. Ctrl+U clears it.
- Enter commits and advances. Tab commits and advances, creating a sibling at
  the end or a child when an object/array is selected.
- Ctrl+O replaces the selected value with an object; Ctrl+A replaces it with an
  array.
- Ctrl+D commits and returns the JSON. Escape or Ctrl+C cancels the session.

On Unix-like systems, `edit()` enters raw mode only when a real JVM console is
available, then restores the exact previous terminal state. `edit(InputStream,
PrintStream)` is deterministic for pipes, tests, and embedded applications and
accepts the same escape sequences for arrow keys.
