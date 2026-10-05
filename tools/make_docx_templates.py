#!/usr/bin/env python3
"""Generates the bundled Word task template and the committed DOCX test fixtures.

Run from the repository root (needs python-docx: `pip install python-docx`):

    python3 tools/make_docx_templates.py

Outputs:
  app/src/main/assets/templates/tasks_template.docx   bundled template (mirrors tasks_template.json)
  app/src/test/resources/docx/reordered_columns.docx  columns in another order, bullets, [x] marks
  app/src/test/resources/docx/legacy.doc              old binary .doc header, must be rejected
"""
import datetime
import os

from docx import Document

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET = os.path.join(ROOT, "app/src/main/assets/templates/tasks_template.docx")
FIXTURES = os.path.join(ROOT, "app/src/test/resources/docx")

HEADERS = ["Task", "Date", "Deadline", "Priority", "Estimated Minutes", "Tags", "Notes", "Sub-tasks"]

# Same three tasks as tasks_template.json. The Word format has no per-subtask deadline.
TASKS = [
    ["Finish chapter 3 of the networking course", "2026-10-05", "2026-10-05 20:00", "high", "90",
     "study, networking", "Focus on TCP congestion control.",
     ["Watch lectures 3.1 to 3.4", "Take notes on congestion control", "[ ] Solve the 5 practice questions"]],
    ["Go for a 30 minute walk", "2026-10-06", "", "low", "", "", "", []],
    ["Submit the monthly expense report", "2026-10-07", "2026-10-07", "medium", "", "work", "",
     ["Collect receipts", "Fill in the expense form", "Email it to the finance team"]],
]

INSTRUCTIONS = (
    "Add one task per row in the table below. Only the Task column is required. "
    "Dates use YYYY-MM-DD. Deadlines use YYYY-MM-DD or YYYY-MM-DD HH:mm (24-hour, your phone's local time). "
    "Priority is high, medium or low. Separate tags with commas. "
    "Put each sub-task on its own line in the Sub-tasks cell; start a line with [x] to mark it done. "
    "Delete the example rows before real use."
)


def fixed_metadata(doc):
    # Fixed timestamps keep the generated file byte-stable across runs.
    stamp = datetime.datetime(2026, 1, 1)
    props = doc.core_properties
    props.author = "Focus Tracker"
    props.title = "Focus Tracker tasks"
    props.created = stamp
    props.modified = stamp
    props.last_modified_by = "Focus Tracker"
    props.revision = 1


def fill_cell(cell, value):
    lines = value if isinstance(value, list) else [value]
    cell.text = lines[0] if lines else ""
    for line in lines[1:]:
        cell.add_paragraph(line)


def table_doc(headers, rows, intro=None, heading=None):
    doc = Document()
    fixed_metadata(doc)
    if heading:
        doc.add_heading(heading, level=1)
    if intro:
        doc.add_paragraph(intro)
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    for cell, name in zip(table.rows[0].cells, headers):
        cell.text = name
    for row in rows:
        cells = table.add_row().cells
        for cell, value in zip(cells, row):
            fill_cell(cell, value)
    return doc


def main():
    table_doc(HEADERS, TASKS, intro=INSTRUCTIONS, heading="Focus Tracker tasks").save(ASSET)

    # Fixture: a decoy table first, columns reordered and renamed, bullets/numbering, an empty row.
    doc = Document()
    fixed_metadata(doc)
    doc.add_paragraph("Some notes before the table. Ignore me.")
    decoy = doc.add_table(rows=2, cols=2)
    decoy.rows[0].cells[0].text = "Name of thing"
    decoy.rows[0].cells[1].text = "Value"
    decoy.rows[1].cells[0].text = "not a task"
    headers = ["sub-tasks", "PRIORITY", "Title", "date", "Tags"]
    rows = [
        [["- [x] First step", "• [ ] Second step", "1. Third step", "2) [X] Fourth step", ""], "High", "Reordered task",
         "2026-10-06", "a, b"],
        [["", ""], "", "", "", ""],
        [["* only step"], "urgent", "Second task", "", ""],
        [[], "low", "", "2026-10-06", ""],
    ]
    t = doc.add_table(rows=1, cols=len(headers))
    for cell, name in zip(t.rows[0].cells, headers):
        cell.text = name
    for row in rows:
        for cell, value in zip(t.add_row().cells, row):
            fill_cell(cell, value)
    doc.add_paragraph("Text after the table is ignored.")
    doc.save(os.path.join(FIXTURES, "reordered_columns.docx"))

    # Fixture: the OLE2 compound-file signature that legacy .doc files start with.
    with open(os.path.join(FIXTURES, "legacy.doc"), "wb") as f:
        f.write(bytes([0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1]) + bytes(504))


if __name__ == "__main__":
    main()
