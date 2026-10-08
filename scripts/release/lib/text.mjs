/**
 * Plain-text helpers shared by the release scripts that write notes into archives (README.txt, LOCAL-AI.txt).
 */

/**
 * Word-wraps plain text at `width` columns, each line prefixed with `indent`. A word longer than the width stays on
 * its own line unbroken.
 * @param {string} text
 * @param {{ width?: number, indent?: string }} [options]
 * @returns {string[]} the wrapped lines (empty for empty text)
 */
export function wrapText(text, { width = 100, indent = '' } = {}) {
  const lines = [];
  let line = indent;
  for (const word of text.split(/\s+/).filter(Boolean)) {
    if (line.length > indent.length && line.length + 1 + word.length > width) {
      lines.push(line);
      line = indent + word;
    } else {
      line = line.length > indent.length ? `${line} ${word}` : indent + word;
    }
  }
  if (line.length > indent.length) lines.push(line);
  return lines;
}
