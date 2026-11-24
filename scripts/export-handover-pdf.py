"""Export the versioned handover to PDF with ReportLab (no network or secrets)."""
from __future__ import annotations

import argparse
import html
import re
import textwrap
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Frame, PageBreak, PageTemplate, Paragraph,
    Preformatted, Spacer, Table, TableStyle,
)
from reportlab.platypus.tableofcontents import TableOfContents


INK = colors.HexColor('#17324d')
ACCENT = colors.HexColor('#007e87')
MUTED = colors.HexColor('#526578')
PALE = colors.HexColor('#eef4f8')


def register_fonts():
    win = Path('C:/Windows/Fonts')
    fonts = [('CG', 'segoeui.ttf'), ('CG-Bold', 'segoeuib.ttf'),
             ('CG-Code', 'consola.ttf')]
    if all((win / filename).exists() for _, filename in fonts):
        for name, filename in fonts:
            pdfmetrics.registerFont(TTFont(name, str(win / filename)))
        pdfmetrics.registerFontFamily('CG', normal='CG', bold='CG-Bold',
                                     italic='CG', boldItalic='CG-Bold')
        return 'CG', 'CG-Bold', 'CG-Code'
    return 'Helvetica', 'Helvetica-Bold', 'Courier'


def inline(value, mono):
    value = html.escape(value)
    value = re.sub(r'\[([^\]]+)\]\(([^)]+)\)',
                   r'<link color="#007e87" href="\2">\1</link>', value)
    value = re.sub(r'`([^`]+)`', lambda m:
                   f'<font name="{mono}" size="8">{m[1]}</font>', value)
    value = re.sub(r'\*\*([^*]+)\*\*', r'<b>\1</b>', value)
    return value


class HandoverDoc(BaseDocTemplate):
    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph) and flowable.style.name == 'Section':
            title = flowable.getPlainText()
            key = 'section-' + str(self.seq.nextf('section'))
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(title, key, 0)
            self.notify('TOCEntry', (0, title, self.page, key))


def export(source: Path, output: Path):
    body, bold, mono = register_fonts()
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle('BodyCG', fontName=body, fontSize=9.3,
                             leading=13.6, textColor=INK, spaceAfter=7,
                             splitLongWords=True))
    styles.add(ParagraphStyle('Section', fontName=bold, fontSize=15,
                             leading=19, spaceBefore=18, spaceAfter=10,
                             textColor=INK, keepWithNext=True))
    styles.add(ParagraphStyle('Subsection', fontName=bold, fontSize=11,
                             leading=15, spaceBefore=10, spaceAfter=7,
                             textColor=ACCENT, keepWithNext=True))
    styles.add(ParagraphStyle('CodeCG', fontName=mono, fontSize=7.2,
                             leading=10, leftIndent=9, rightIndent=9,
                             spaceBefore=5, spaceAfter=9, backColor=PALE,
                             borderPadding=7))
    styles.add(ParagraphStyle('CellCG', parent=styles['BodyCG'],
                             fontSize=8.1, leading=11.8, spaceAfter=1))
    styles.add(ParagraphStyle('ListCG', parent=styles['BodyCG'], leftIndent=14,
                             firstLineIndent=-10, spaceAfter=5))
    styles.add(ParagraphStyle('Cover', fontName=bold, fontSize=29,
                             leading=35, textColor=INK, spaceAfter=19))
    styles.add(ParagraphStyle('Kicker', fontName=bold, fontSize=10,
                             leading=15, textColor=ACCENT, spaceAfter=15))

    output.parent.mkdir(parents=True, exist_ok=True)
    doc = HandoverDoc(str(output), pagesize=A4, rightMargin=48, leftMargin=48,
                      topMargin=57, bottomMargin=46,
                      title='CircleGuard - Handover para Claude',
                      author='CircleGuard', subject='Estado y continuidad de AWS y CI/CD')
    width, height = A4

    def page_decor(canvas, document):
        canvas.saveState()
        canvas.setStrokeColor(ACCENT)
        canvas.setLineWidth(1)
        canvas.line(48, height - 36, width - 48, height - 36)
        canvas.setFont(bold, 8)
        canvas.setFillColor(MUTED)
        canvas.drawString(48, height - 28, 'CIRCLEGUARD  /  CONTINUIDAD TÉCNICA')
        canvas.setFont(body, 7.4)
        canvas.drawString(48, 26, 'Auditoría: 23 nov 2026  |  Sin credenciales en texto plano')
        canvas.drawRightString(width - 48, 26, str(document.page))
        canvas.restoreState()

    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height,
                  leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
    doc.addPageTemplates(PageTemplate(id='main', frames=frame, onPage=page_decor))
    story = [Spacer(1, 45), Paragraph('AWS / CI/CD / RELEASE', styles['Kicker']),
             Paragraph('CircleGuard<br/>Guía de continuidad<br/>para Claude y el equipo', styles['Cover']),
             Paragraph('Rama: fix/release-1.1.0-estabilizacion', styles['BodyCG']),
             Spacer(1, 15), Paragraph(
                 '<b>Estado:</b> infraestructura y middleware dev operativos. '
                 'El resultado de imágenes se detalla en la sección 6. '
                 'La release oficial y el despliegue completo siguen sujetos '
                 'a las verificaciones de esta guía.', styles['BodyCG']),
             Spacer(1, 12), Paragraph(
                 'Incluye acceso seguro a AWS desde Claude/Codex, operación de CI, '
                 'responsabilidad del webhook, costos, promoción, alertas, rollback '
                 'y cierre a main.', styles['BodyCG']),
             Spacer(1, 14), Paragraph(
                 '<b>Fuente ejecutable:</b> docs/handover-claude-release.md. '
                 'Copiar los comandos desde ese archivo: las líneas largas del '
                 'PDF se ajustan únicamente para lectura.', styles['BodyCG']),
             PageBreak(), Paragraph('Índice', styles['Cover'])]
    toc = TableOfContents()
    toc.levelStyles = [ParagraphStyle('TOC', fontName=body, fontSize=9.2,
                                      leading=16.5, textColor=INK,
                                      leftIndent=0, firstLineIndent=0)]
    story.extend([toc, PageBreak()])
    lines = source.read_text(encoding='utf-8').splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip() or line.startswith('# '):
            i += 1
            continue
        if line.startswith('```'):
            block = []
            i += 1
            while i < len(lines) and not lines[i].startswith('```'):
                block.extend(textwrap.wrap(lines[i].expandtabs(4), width=110,
                                          replace_whitespace=False,
                                          drop_whitespace=False) or [''])
                i += 1
            story.append(Preformatted('\n'.join(block), styles['CodeCG']))
            i += 1
            continue
        if line.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                cells = [cell.strip() for cell in lines[i].strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?', cell) for cell in cells):
                    rows.append([Paragraph(inline(cell, mono), styles['CellCG'])
                                 for cell in cells])
                i += 1
            count = len(rows[0])
            fractions = [0.37, 0.63] if count == 2 else [0.22, 0.29, 0.49]
            table = Table(rows, colWidths=[doc.width * f for f in fractions],
                          repeatRows=1, hAlign='LEFT')
            table.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), PALE),
                ('VALIGN', (0, 0), (-1, -1), 'TOP'),
                ('LINEBELOW', (0, 0), (-1, 0), 0.8, ACCENT),
                ('LINEBELOW', (0, 1), (-1, -1), 0.3, colors.HexColor('#d5dfe7')),
                ('LEFTPADDING', (0, 0), (-1, -1), 7),
                ('RIGHTPADDING', (0, 0), (-1, -1), 7),
                ('TOPPADDING', (0, 0), (-1, -1), 7),
                ('BOTTOMPADDING', (0, 0), (-1, -1), 7),
            ]))
            story.extend([table, Spacer(1, 9)])
            continue
        if line.startswith('## '):
            story.append(Paragraph(inline(line[3:], mono), styles['Section']))
        elif line.startswith('### '):
            story.append(Paragraph(inline(line[4:], mono), styles['Subsection']))
        elif re.match(r'^(- |\d+\. )', line):
            story.append(Paragraph(inline(line, mono), styles['ListCG']))
        else:
            paragraph = [line]
            while i + 1 < len(lines) and lines[i + 1].strip() and not re.match(
                    r'^(#|\||```|- |\d+\. )', lines[i + 1]):
                i += 1
                paragraph.append(lines[i])
            story.append(Paragraph(inline(' '.join(paragraph), mono), styles['BodyCG']))
        i += 1
    doc.multiBuild(story)
    print(output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=Path('docs/handover-claude-release.md'))
    parser.add_argument('--output', type=Path, default=Path('output/pdf/circleguard-handover-claude.pdf'))
    args = parser.parse_args()
    export(args.source, args.output)
