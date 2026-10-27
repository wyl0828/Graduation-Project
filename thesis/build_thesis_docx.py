# -*- coding: utf-8 -*-
"""
毕业论文 Word (.docx) 生成脚本
将 thesis/chapters 目录下的各分章合流，并转换为符合中国高校本科毕业设计论文规范的标准 Word 文档。
"""

import os
import re
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

def set_cell_border(cell, **kwargs):
    """
    为单元格设置边框（三线表规范）
    kwargs: top, bottom, left, right, insideH, insideV
    values: dict(sz=12, val='single', color='000000')
    """
    tcPr = cell._tc.get_or_add_tcPr()
    tcBorders = parse_xml(f'<w:tcBorders {nsdecls("w")}/>')
    for edge in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        edge_data = kwargs.get(edge)
        if edge_data:
            tag = f'w:{edge}'
            element = parse_xml(f'<{tag} {nsdecls("w")} w:val="{edge_data.get("val", "single")}" w:sz="{edge_data.get("sz", 4)}" w:space="0" w:color="{edge_data.get("color", "auto")}"/>')
            tcBorders.append(element)
        else:
            tag = f'w:{edge}'
            element = parse_xml(f'<{tag} {nsdecls("w")} w:val="none"/>')
            tcBorders.append(element)
    tcPr.append(tcBorders)

def set_cell_shading(cell, color_hex):
    """设置单元格底纹"""
    shading = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{color_hex}"/>')
    cell._tc.get_or_add_tcPr().append(shading)

def format_run(run, font_name="宋体", size_pt=12, bold=False, italic=False, color_rgb=(0,0,0)):
    run.font.name = "Times New Roman"
    run.element.rPr.rFonts.set(qn("w:eastAsia"), font_name)
    run.font.size = Pt(size_pt)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = RGBColor(*color_rgb)

def add_styled_paragraph(doc, text="", style_type="body", align=WD_ALIGN_PARAGRAPH.JUSTIFY):
    """
    添加格式化段落
    style_type:
      - 'title': 二号黑体，居中，加粗
      - 'subtitle': 四号宋体，居中
      - 'heading1': 三号黑体，加粗，段前12pt，段后6pt
      - 'heading2': 四号黑体，加粗，段前6pt，段后3pt
      - 'heading3': 小四黑体，加粗，段前3pt，段后0pt
      - 'heading4': 小四黑体，加粗
      - 'body': 小四宋体，首行缩进2字符，1.5倍行距
      - 'body_noindent': 小四宋体，无缩进，1.5倍行距
      - 'caption': 五号黑体/宋体，居中，单倍行距
      - 'quote': 小四楷体，左右缩进，1.25倍行距
    """
    p = doc.add_paragraph()
    p.alignment = align
    p_format = p.paragraph_format

    if style_type == "title":
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_format.space_before = Pt(24)
        p_format.space_after = Pt(18)
        p_format.line_spacing = 1.25
        run = p.add_run(text)
        format_run(run, font_name="黑体", size_pt=22, bold=True)
    elif style_type == "subtitle":
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_format.space_before = Pt(6)
        p_format.space_after = Pt(24)
        run = p.add_run(text)
        format_run(run, font_name="宋体", size_pt=14, bold=False)
    elif style_type == "heading1":
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p_format.space_before = Pt(16)
        p_format.space_after = Pt(8)
        p_format.line_spacing = 1.25
        p_format.keep_with_next = True
        run = p.add_run(text)
        format_run(run, font_name="黑体", size_pt=16, bold=True)
    elif style_type == "heading2":
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p_format.space_before = Pt(10)
        p_format.space_after = Pt(4)
        p_format.line_spacing = 1.25
        p_format.keep_with_next = True
        run = p.add_run(text)
        format_run(run, font_name="黑体", size_pt=14, bold=True)
    elif style_type == "heading3":
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p_format.space_before = Pt(6)
        p_format.space_after = Pt(2)
        p_format.line_spacing = 1.25
        p_format.keep_with_next = True
        run = p.add_run(text)
        format_run(run, font_name="黑体", size_pt=12, bold=True)
    elif style_type == "heading4":
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p_format.space_before = Pt(4)
        p_format.space_after = Pt(2)
        p_format.line_spacing = 1.25
        p_format.keep_with_next = True
        run = p.add_run(text)
        format_run(run, font_name="黑体", size_pt=12, bold=True)
    elif style_type == "body":
        p_format.first_line_indent = Pt(24)  # 2字符首行缩进
        p_format.line_spacing = 1.5
        p_format.space_after = Pt(2)
        add_formatted_runs(p, text, font_name="宋体", size_pt=12)
    elif style_type == "body_noindent":
        p_format.line_spacing = 1.5
        p_format.space_after = Pt(2)
        add_formatted_runs(p, text, font_name="宋体", size_pt=12)
    elif style_type == "caption":
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_format.space_before = Pt(4)
        p_format.space_after = Pt(6)
        run = p.add_run(text)
        format_run(run, font_name="黑体", size_pt=10.5, bold=True)
    elif style_type == "quote":
        p_format.left_indent = Pt(24)
        p_format.right_indent = Pt(24)
        p_format.line_spacing = 1.25
        p_format.space_before = Pt(4)
        p_format.space_after = Pt(4)
        run = p.add_run(text)
        format_run(run, font_name="楷体", size_pt=11, bold=False, color_rgb=(60,60,60))
    elif style_type == "code":
        p_format.left_indent = Pt(12)
        p_format.right_indent = Pt(12)
        p_format.space_before = Pt(2)
        p_format.space_after = Pt(2)
        p_format.line_spacing = 1.15
        run = p.add_run(text)
        run.font.name = "Consolas"
        run.element.rPr.rFonts.set(qn("w:eastAsia"), "Consolas")
        run.font.size = Pt(9.5)
        run.font.color.rgb = RGBColor(40, 40, 40)
    return p

def add_formatted_runs(paragraph, text, font_name="宋体", size_pt=12):
    """解析 Markdown 内联样式：加粗 **...**，行内代码 `...` 等"""
    pattern = re.compile(r'(\*\*.*?\*\*|`.*?`|\$.*?\$|\[.*?\]\(.*?\))')
    tokens = pattern.split(text)
    for token in tokens:
        if not token:
            continue
        if token.startswith('**') and token.endswith('**'):
            content = token[2:-2]
            run = paragraph.add_run(content)
            format_run(run, font_name="黑体", size_pt=size_pt, bold=True)
        elif token.startswith('`') and token.endswith('`'):
            content = token[1:-1]
            run = paragraph.add_run(content)
            run.font.name = "Consolas"
            run.element.rPr.rFonts.set(qn("w:eastAsia"), "Consolas")
            run.font.size = Pt(size_pt - 1)
            run.font.color.rgb = RGBColor(160, 40, 40)
        elif token.startswith('$') and token.endswith('$'):
            content = token[1:-1]
            run = paragraph.add_run(content)
            run.font.name = "Times New Roman"
            run.font.italic = True
            run.font.size = Pt(size_pt)
        elif token.startswith('[') and '](' in token and token.endswith(')'):
            match = re.match(r'\[(.*?)\]\((.*?)\)', token)
            if match:
                content = match.group(1)
                run = paragraph.add_run(content)
                format_run(run, font_name=font_name, size_pt=size_pt, bold=True)
            else:
                run = paragraph.add_run(token)
                format_run(run, font_name=font_name, size_pt=size_pt)
        else:
            run = paragraph.add_run(token)
            format_run(run, font_name=font_name, size_pt=size_pt)

def create_table_from_markdown(doc, table_lines):
    """将 Markdown 表格转换为标准三线表（Top/Bottom 1.5pt，Header 0.75pt）"""
    rows_data = []
    for line in table_lines:
        line = line.strip()
        if not line.startswith('|') or not line.endswith('|'):
            continue
        cells = [c.strip() for c in line.split('|')[1:-1]]
        # 忽略分隔行 | --- | --- |
        if all(re.match(r'^:?-+:?$', c) for c in cells):
            continue
        rows_data.append(cells)

    if not rows_data:
        return

    num_rows = len(rows_data)
    num_cols = max(len(r) for r in rows_data)

    table = doc.add_table(rows=num_rows, cols=num_cols)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = True

    # 三线表粗细设置
    thick_border = {'val': 'single', 'sz': 12, 'color': '000000'}  # 1.5pt
    thin_border = {'val': 'single', 'sz': 6, 'color': '000000'}    # 0.75pt
    light_inner = {'val': 'single', 'sz': 4, 'color': 'CCCCCC'}    # 0.5pt 浅灰内部横线

    for r_idx, row in enumerate(table.rows):
        # 保证行不跨页断裂
        trPr = row._tr.get_or_add_trPr()
        trPr.append(parse_xml(f'<w:cantSplit {nsdecls("w")}/>'))

        # 表头行设置重复表头
        if r_idx == 0:
            trPr.append(parse_xml(f'<w:tblHeader {nsdecls("w")}/>'))

        row_data = rows_data[r_idx]
        for c_idx, cell in enumerate(row.cells):
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            text = row_data[c_idx] if c_idx < len(row_data) else ""
            cell.text = ""
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER if r_idx == 0 else WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.space_before = Pt(3)
            p.paragraph_format.space_after = Pt(3)
            p.paragraph_format.line_spacing = 1.15

            if r_idx == 0:
                add_formatted_runs(p, text, font_name="黑体", size_pt=10)
                set_cell_shading(cell, "F2F2F2")
                set_cell_border(cell, top=thick_border, bottom=thin_border)
            elif r_idx == num_rows - 1:
                add_formatted_runs(p, text, font_name="宋体", size_pt=9.5)
                set_cell_border(cell, bottom=thick_border, top=light_inner)
            else:
                add_formatted_runs(p, text, font_name="宋体", size_pt=9.5)
                set_cell_border(cell, top=light_inner)

    # 表格后加空行
    doc.add_paragraph().paragraph_format.space_after = Pt(4)

def parse_and_append_markdown(doc, md_content):
    """逐步解析 Markdown 文本并添加到 docx 文档"""
    lines = md_content.split('\n')
    i = 0
    in_code_block = False
    code_lines = []
    in_table = False
    table_lines = []

    while i < len(lines):
        line = lines[i]
        raw_strip = line.strip()

        # 处理代码块
        if raw_strip.startswith('```'):
            if in_code_block:
                # 结束代码块
                in_code_block = False
                code_text = "\n".join(code_lines)
                # 为代码块创建一个单单元格带浅灰底纹的边框容器
                box_tbl = doc.add_table(rows=1, cols=1)
                box_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
                cell = box_tbl.cell(0, 0)
                set_cell_shading(cell, "F8F9FA")
                thin_line = {'val': 'single', 'sz': 4, 'color': 'E2E8F0'}
                set_cell_border(cell, top=thin_line, bottom=thin_line, left=thin_line, right=thin_line)
                cell.text = ""
                for cl in code_lines:
                    p = cell.add_paragraph()
                    p.paragraph_format.space_before = Pt(0)
                    p.paragraph_format.space_after = Pt(0)
                    p.paragraph_format.line_spacing = 1.15
                    run = p.add_run(cl)
                    run.font.name = "Consolas"
                    run.element.rPr.rFonts.set(qn("w:eastAsia"), "Consolas")
                    run.font.size = Pt(9)
                    run.font.color.rgb = RGBColor(40, 40, 40)
                doc.add_paragraph().paragraph_format.space_after = Pt(4)
                code_lines = []
            else:
                # 开始代码块
                in_code_block = True
                code_lines = []
            i += 1
            continue

        if in_code_block:
            code_lines.append(line)
            i += 1
            continue

        # 处理表格
        if raw_strip.startswith('|') and raw_strip.endswith('|'):
            if not in_table:
                in_table = True
                table_lines = []
            table_lines.append(raw_strip)
            i += 1
            continue
        else:
            if in_table:
                in_table = False
                create_table_from_markdown(doc, table_lines)
                table_lines = []

        # 处理空行与水平分割线
        if not raw_strip or raw_strip in ('---', '***', '___'):
            i += 1
            continue

        # 处理标题
        if raw_strip.startswith('# '):
            text = raw_strip[2:].strip()
            add_styled_paragraph(doc, text, style_type="heading1")
        elif raw_strip.startswith('## '):
            text = raw_strip[3:].strip()
            add_styled_paragraph(doc, text, style_type="heading2")
        elif raw_strip.startswith('### '):
            text = raw_strip[4:].strip()
            add_styled_paragraph(doc, text, style_type="heading3")
        elif raw_strip.startswith('#### '):
            text = raw_strip[5:].strip()
            add_styled_paragraph(doc, text, style_type="heading4")
        # 处理引用块
        elif raw_strip.startswith('> '):
            text = raw_strip[2:].strip()
            add_styled_paragraph(doc, text, style_type="quote")
        # 处理无序列表
        elif raw_strip.startswith('- ') or raw_strip.startswith('* '):
            text = raw_strip[2:].strip()
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Pt(28)
            p.paragraph_format.first_line_indent = Pt(-14)
            p.paragraph_format.line_spacing = 1.35
            run_bullet = p.add_run("•  ")
            format_run(run_bullet, font_name="宋体", size_pt=11, bold=True)
            add_formatted_runs(p, text, font_name="宋体", size_pt=12)
        # 处理数字有序列表
        elif re.match(r'^\d+\.\s+', raw_strip):
            match = re.match(r'^(\d+\.)\s+(.*)$', raw_strip)
            num_prefix = match.group(1)
            content = match.group(2)
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Pt(28)
            p.paragraph_format.first_line_indent = Pt(-14)
            p.paragraph_format.line_spacing = 1.35
            run_num = p.add_run(num_prefix + "  ")
            format_run(run_num, font_name="Times New Roman", size_pt=11, bold=True)
            add_formatted_runs(p, content, font_name="宋体", size_pt=12)
        else:
            add_styled_paragraph(doc, raw_strip, style_type="body")

        i += 1

    if in_table and table_lines:
        create_table_from_markdown(doc, table_lines)

def build_full_thesis_docx(output_path):
    print("开始构建毕业论文标准 Word 文档...")
    doc = docx.Document()

    # 1. 页面设置（A4 纸，标准高校工科边距）
    sections = doc.sections
    for section in sections:
        section.page_width = Inches(8.27)    # 210mm A4
        section.page_height = Inches(11.69)  # 297mm A4
        section.top_margin = Inches(0.98)    # 25mm
        section.bottom_margin = Inches(0.98) # 25mm
        section.left_margin = Inches(1.18)   # 30mm (留装订线侧)
        section.right_margin = Inches(0.98)  # 25mm

    # 2. 封面页
    p_cover_top = doc.add_paragraph()
    p_cover_top.paragraph_format.space_before = Pt(72)
    
    add_styled_paragraph(doc, "普通高等学校本科毕业设计（论文）", style_type="subtitle")
    add_styled_paragraph(doc, "基于 Spring Boot 与适老化设计的\n社区居家养老服务系统设计与实现", style_type="title")
    
    p_info = doc.add_paragraph()
    p_info.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_info.paragraph_format.space_before = Pt(120)
    p_info.paragraph_format.line_spacing = 1.8
    
    infos = [
        ("课题名称：", "基于 Spring Boot 与适老化设计的社区居家养老服务系统设计与实现"),
        ("学生姓名：", "（以答辩审批登记为准）"),
        ("学    号：", "（学生学号）"),
        ("专    业：", "软件工程 / 计算机科学与技术"),
        ("指导教师：", "（指导教师职称与姓名）"),
        ("完成日期：", "2026 年 10 月")
    ]
    for label, val in infos:
        p_row = doc.add_paragraph()
        p_row.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_row.paragraph_format.space_after = Pt(4)
        run_lbl = p_row.add_run(f"    {label}  ")
        format_run(run_lbl, font_name="黑体", size_pt=12, bold=True)
        run_val = p_row.add_run(f" {val} ")
        format_run(run_val, font_name="宋体", size_pt=12, bold=False)

    doc.add_page_break()

    # 3. 中英文摘要页
    abstract_path = "thesis/chapters/00_摘要与Abstract.md"
    if os.path.exists(abstract_path):
        with open(abstract_path, "r", encoding="utf-8") as f:
            abs_content = f.read()
        parse_and_append_markdown(doc, abs_content)
        doc.add_page_break()

    # 4. 目录页
    toc_p = doc.add_paragraph()
    toc_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    toc_p.paragraph_format.space_before = Pt(16)
    toc_p.paragraph_format.space_after = Pt(16)
    run_toc = toc_p.add_run("目    录")
    format_run(run_toc, font_name="黑体", size_pt=16, bold=True)

    toc_items = [
        ("1 绪论", "1"),
        ("  1.1 研究背景与实际需求", "1"),
        ("  1.2 研究目的与意义", "1"),
        ("    1.2.1 实践意义", "1"),
        ("    1.2.2 理论意义", "2"),
        ("  1.3 国内外研究现状", "2"),
        ("    1.3.1 智慧社区养老系统国内外研究现状", "2"),
        ("    1.3.2 适老化人机交互与认知负荷研究现状", "3"),
        ("    1.3.3 现有研究评述与研究缺口", "3"),
        ("  1.4 主要研究内容与方法", "4"),
        ("    1.4.1 主要研究内容", "4"),
        ("    1.4.2 研究方法与技术路线", "4"),
        ("2 相关技术与理论基础", "5"),
        ("  2.1 核心技术框架", "5"),
        ("    2.1.1 Spring Boot 后端工程框架", "5"),
        ("    2.1.2 Vue 3 与 Uni-app 跨端框架", "5"),
        ("    2.1.3 前后端分离与单体分层架构", "6"),
        ("  2.2 关键技术与机制", "6"),
        ("    2.2.1 基于 JWT 与受控会话的身份认证机制", "6"),
        ("    2.2.2 异步事件通知与短轮询即时刷新机制", "7"),
        ("    2.2.3 基于有限状态机（FSM）的业务流转机制", "7"),
        ("    2.2.4 社区网格化治理与属地数据权限隔离机制", "7"),
        ("    2.2.5 数据库演进管理与行级乐观锁并发控制", "8"),
        ("  2.3 适老化设计的理论基础", "8"),
        ("    2.3.1 认知负荷理论（Cognitive Load Theory）", "8"),
        ("    2.3.2 国家互联网应用适老化设计通用规范", "9"),
        ("3 系统需求分析", "10"),
        ("  3.1 核心业务流程分析", "10"),
        ("    3.1.1 老人健康体征监测与异常处理流程", "10"),
        ("    3.1.2 突发 SOS 紧急告警与联动建单流程", "10"),
        ("    3.1.3 助老服务预约与网格履约闭环流程", "11"),
        ("  3.2 系统角色划分与权限边界", "11"),
        ("  3.3 系统用例分析", "12"),
        ("  3.4 系统非功能需求分析", "13"),
        ("  3.5 系统可行性分析", "14"),
        ("4 系统设计", "15"),
        ("  4.1 系统设计原则", "15"),
        ("  4.2 系统总体物理架构与技术体系设计", "15"),
        ("  4.3 受助与监护端（小程序）模块设计", "16"),
        ("  4.4 线下履约端（移动工作台）模块设计", "17"),
        ("  4.5 社区综合管理端（Web 后台）模块设计", "17"),
        ("  4.6 数据库系统设计", "18"),
        ("    4.6.1 数据库概念模型设计（E-R 图）", "18"),
        ("    4.6.2 数据库逻辑结构设计（10 张核心业务表）", "18"),
        ("    4.6.3 数据库版本演进与 Flyway 迁移策略", "22"),
        ("  4.7 核心特色机制详细设计", "22"),
        ("    4.7.1 助老服务与 SOS 求助统一工单转化模型", "22"),
        ("    4.7.2 基于有限状态机的工单流转状态转移矩阵", "23"),
        ("    4.7.3 基于社区网格化的派发策略与防越权校验", "24"),
        ("    4.7.4 基于乐观锁的并发调度控制机制", "25"),
        ("    4.7.5 履约凭证存证与防篡改机制", "26"),
        ("5 系统实现", "27"),
        ("  5.1 受助与监护端功能实现", "27"),
        ("    5.1.1 老人适老主页与极简服药打卡实现", "27"),
        ("    5.1.2 一键突发 SOS 紧急求助实现", "28"),
        ("    5.1.3 子女端多维体征趋势与代预约实现", "28"),
        ("  5.2 线下履约端功能实现", "29"),
        ("    5.2.1 护工专属工单列表与状态更新实现", "29"),
        ("    5.2.2 现场服务处置与照片证据上传实现", "30"),
        ("  5.3 社区综合管理端功能实现", "31"),
        ("    5.3.1 老人档案管理与亲情绑定核验实现", "31"),
        ("    5.3.2 社区网格化派单与工单审核结案实现", "31"),
        ("    5.3.3 社区运营与预警处置时效看板实现", "32"),
        ("    5.3.4 仿真环境与异常数据发生器实现", "32"),
        ("6 系统测试与分析", "33"),
        ("  6.1 测试目标与环境搭建", "33"),
        ("  6.2 系统功能与权限测试用例设计", "33"),
        ("  6.3 核心流转机制与安全并发测试", "35"),
        ("  6.4 模拟高并发性能测试与分析", "36"),
        ("7 结束语", "38"),
        ("  7.1 工作总结", "38"),
        ("  7.2 系统不足与未来展望", "39"),
        ("参考文献", "40"),
        ("致谢", "42")
    ]
    for title_text, pg in toc_items:
        p_t = doc.add_paragraph()
        p_t.paragraph_format.space_before = Pt(1)
        p_t.paragraph_format.space_after = Pt(1)
        p_t.paragraph_format.line_spacing = 1.2
        r_title = p_t.add_run(title_text)
        is_h1 = not title_text.startswith(" ")
        format_run(r_title, font_name="黑体" if is_h1 else "宋体", size_pt=10.5, bold=is_h1)

    doc.add_page_break()

    # 5. 正文各章节拼合
    chapter_files = [
        "thesis/chapters/第1章_绪论.md",
        "thesis/chapters/第2章_相关技术与理论基础.md",
        "thesis/chapters/第3章_系统需求分析.md",
        ("thesis/chapters/第4章_系统设计.md", "thesis/chapters/第4章_核心机制专项设计_工单流转与调度.md"),
        "thesis/chapters/第5章_系统实现.md",
        "thesis/chapters/第6章_系统测试与分析.md",
        "thesis/chapters/第7章_结束语与致谢.md"
    ]

    for item in chapter_files:
        if isinstance(item, tuple):
            main_ch, sub_ch = item
            with open(main_ch, "r", encoding="utf-8") as f:
                content_main = f.read()
            with open(sub_ch, "r", encoding="utf-8") as f:
                content_sub = f.read()
            # 将 4.7 替换为完整的详细设计
            marker = "*(注：4.7.1"
            if marker in content_main:
                idx = content_main.find(marker)
                combined = content_main[:idx] + "\n\n" + content_sub
            else:
                combined = content_main + "\n\n" + content_sub
            parse_and_append_markdown(doc, combined)
        else:
            with open(item, "r", encoding="utf-8") as f:
                ch_content = f.read()
            parse_and_append_markdown(doc, ch_content)

        doc.add_page_break()

    # 6. 参考文献章节
    add_styled_paragraph(doc, "参考文献", style_type="heading1")
    ref_list = [
        "[1] 工业和信息化部办公厅. 关于进一步抓好互联网应用适老化及无障碍改造专项行动实施工作的通知：工信厅信管函〔2021〕67 号[Z]. 2021.",
        "[2] 全国人民代表大会常务委员会. 中华人民共和国无障碍环境建设法[Z]. 2023-06-28 通过，2023-09-01 施行.",
        "[3] GB/T 37668—2019 信息技术 互联网内容无障碍可访问性技术要求与测试方法[S]. 2019-08-30 发布，2020-03-01 实施.",
        "[4] GB/T 8567—2006 计算机软件文档编制规范[S]. 2006-03-14 发布，2006-07-01 实施.",
        "[5] GB/T 25000.51—2016 系统与软件工程 系统与软件质量要求和评价（SQuaRE）第 51 部分：就绪可用软件产品（RUSP）的质量要求和测试细则[S]. 2016-10-13 发布，2017-05-01 实施.",
        "[6] World Wide Web Consortium. Web Content Accessibility Guidelines (WCAG) 2.2[S/OL]. 2023. https://www.w3.org/TR/WCAG22/",
        "[7] 国家卫生健康委员会. 我国养老呈'9073'格局（老龄健康司司长王海东答问）[EB/OL]. 2021-04-08.",
        "[8] Sweller J. Cognitive load during problem solving: Effects on learning[J]. Cognitive Science, 1988, 12(2): 257-285.",
        "[9] Sweller J, Ayres P, Kalyuga S. Cognitive Load Theory[M]. New York: Springer, 2011.",
        "[10] Hou G, Anicetus U, He J. How to design font size for older adults: A systematic literature review with a mobile device[J]. Frontiers in Psychology, 2022, 13.",
        "[11] Bernard M, Liao C H, Mills M. The effects of font type and size on the legibility and reading time of online text by older adults[C]. CHI '01 Extended Abstracts, 2001: 175-176.",
        "[12] Arnhold M, Quade M, Kirch W. Mobile Applications for Diabetics: A Systematic Review and Expert-Based Usability Evaluation Considering the Special Requirements of Diabetes Patients Age 50 Years or Older[J]. Journal of Medical Internet Research, 2014, 16(4): e104.",
        "[13] Wang S, Bolling K, Mao W, et al. Technology to Support Aging in Place: Older Adults' Perspectives[J]. Healthcare, 2019, 7(2): 60.",
        "[14] Johnson J A. Myths about digital technology and older adults[J]. Nature Aging, 2022, 2(12): 1073-1076.",
        "[15] Choi J, Glose S, Tarte V. Mobile Health App Adoption Among Older Adults: A Technology Acceptance Model Approach[J]. Journal of Gerontological Nursing, 2026, 52(4): 11-16.",
        "[16] Zhang R. Design and implementation of home-based elderly care system based on community service[C]. 2nd International Conference on Internet of Things and Smart City (IoTSC 2022). SPIE, 2022: 32.",
        "[17] Xiong L, Peng B, Xiong J. Design and Implementation of Community Health and Elderly Care Service Platform by Computer Technology[C]. 2023 IEEE 6th ICKII, 2023: 342-344.",
        "[18] Kong D, Fang X, Chen Z, et al. Design and Implementation of the 'Elder Care Without Worries' Integrated Smart Elderly Care Platform[C]. Proc. 2024 Int'l Conf. on Smart Healthcare and Wearable Intelligent Devices. ACM, 2024: 115-120.",
        "[19] Zhou H, Wang J. Design of Intelligent Elderly Care Service System Combined With the Medical Care and Elderly Care Based on IoT[C]. Proc. 2023 4th Int'l Symp. on AI for Medicine Science. ACM, 2023: 992-996.",
        "[20] Bai C, Zhu Z, Wang T, et al. Long-term care insurance and accessibility of home and community-based services for older adults: evidence from China[J]. Health Economics, Policy and Law, 2026: 1-16."
    ]
    for ref_str in ref_list:
        p_ref = doc.add_paragraph()
        p_ref.paragraph_format.left_indent = Pt(28)
        p_ref.paragraph_format.first_line_indent = Pt(-28)  # 悬挂缩进
        p_ref.paragraph_format.line_spacing = 1.25
        p_ref.paragraph_format.space_after = Pt(4)
        run_r = p_ref.add_run(ref_str)
        format_run(run_r, font_name="宋体", size_pt=10.5)

    # 7. 保存文档
    doc.save(output_path)
    print(f"毕业论文 Word 初稿生成成功！输出文件: {output_path}")

if __name__ == "__main__":
    out_file = os.path.abspath("thesis/社区居家养老服务系统_毕业论文初稿.docx")
    build_full_thesis_docx(out_file)
