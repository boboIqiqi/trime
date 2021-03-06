/*
 * Copyright 2015 osfans
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.osfans.trime;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;

import android.util.Log;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spannable;
import android.text.style.*;
import android.text.Layout;
import android.text.TextPaint;
import android.annotation.TargetApi;
import android.os.Parcel;
import android.text.method.LinkMovementMethod;
import android.os.Build.VERSION_CODES;
import android.os.Build.VERSION;

import java.util.Map;
import java.util.List;

/** 編碼區，顯示已輸入的按鍵編碼，可使用方向鍵或觸屏移動光標位置 */
public class Composition extends TextView {
  private int key_text_size, text_size, label_text_size, candidate_text_size, comment_text_size;
  private int key_text_color, text_color, label_color, candidate_text_color, comment_text_color;
  private int hilited_text_color, hilited_candidate_text_color, hilited_comment_text_color;
  private int back_color, hilited_back_color, hilited_candidate_back_color;
  private Integer key_back_color;
  private Typeface tfText, tfLabel, tfCandidate, tfComment;
  private int composition_pos[] = new int[2];
  private int max_length, sticky_lines;
  private int max_entries = Candidate.MAX_CANDIDATE_COUNT;
  private boolean candidate_use_cursor, show_comment;
  private int highlightIndex;
  private List<Map<String,Object>> components;
  private SpannableStringBuilder ss;
  private int span = 0;
  private String movable;
  private int move_pos[] = new int[2];
  private boolean first_move = true;
  private float mDx, mDy;
  private int mCurrentX, mCurrentY;
  private int candidate_num;
  private boolean all_phrases;

  private class CompositionSpan extends UnderlineSpan{
      public CompositionSpan() {
          super();
      }
      @Override
      public void updateDrawState(TextPaint ds) {
          ds.setTypeface(tfText);
          ds.setColor(text_color);
          ds.bgColor = back_color;
      }
  }

  private class CandidateSpan extends ClickableSpan{
      int index;
      Typeface tf;
      int hi_text, hi_back, text;
      public CandidateSpan(int i, Typeface _tf, int _hi_text, int _hi_back, int _text) {
          super();
          index = i;
          tf = _tf;
          hi_text = _hi_text;
          hi_back = _hi_back;
          text = _text;
      }
      @Override
      public void onClick(View tv) {
         Trime.getService().onPickCandidate(index);
      }
      @Override
      public void updateDrawState(TextPaint ds) {
          ds.setUnderlineText(false);
          ds.setTypeface(tf);
          if (index == highlightIndex) {
            ds.setColor(hi_text);
            ds.bgColor = hi_back;
          } else {
            ds.setColor(text);
          }
      }
  }

  private class EventSpan extends ClickableSpan{
      Event event;
      public EventSpan(Event e) {
          super();
          event = e;
      }

      @Override
      public void onClick(View tv) {
         Trime.getService().onEvent(event);
      }
      @Override
      public void updateDrawState(TextPaint ds) {
          ds.setUnderlineText(false);
          ds.setColor(key_text_color);
          if (key_back_color != null) ds.bgColor = key_back_color;
      }
  }

  @TargetApi(21)
  public class LetterSpacingSpan extends UnderlineSpan {
      private float letterSpacing;

      /**
       * @param letterSpacing 字符間距
       */
      public LetterSpacingSpan(float letterSpacing) {
          this.letterSpacing = letterSpacing;
      }

      @Override
      public void updateDrawState(TextPaint ds) {
          ds.setLetterSpacing(letterSpacing);
      }

  }

  public Composition(Context context, AttributeSet attrs) {
    super(context, attrs);
    reset();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getAction();
    if (action == MotionEvent.ACTION_UP) {
      int n = getOffsetForPosition(event.getX(), event.getY());
      if (composition_pos[0] <= n && n <= composition_pos[1]) {
        String s = getText().toString().substring(n, composition_pos[1]).replace(" ", "").replace("‸", "");
        n = Rime.RimeGetInput().length() - s.length(); //從右側定位
        Rime.RimeSetCaretPos(n);
        Trime.getService().updateComposing();
        return true;
      }
    } else if (!movable.contentEquals("false") && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN)) {
      int n = getOffsetForPosition(event.getX(), event.getY());
      if (move_pos[0] <= n && n <= move_pos[1]) {
        if (action == MotionEvent.ACTION_DOWN) {
          if (first_move || movable.contentEquals("once")) {
            first_move = false;
            int location[] = new int[2];
            getLocationOnScreen(location);
            mCurrentX =location[0];
            mCurrentY = location[1];
          }
          mDx = mCurrentX - event.getRawX();
          mDy = mCurrentY - event.getRawY();
        } else { //MotionEvent.ACTION_MOVE
          mCurrentX = (int) (event.getRawX() + mDx);
          mCurrentY = (int) (event.getRawY() + mDy);
          Trime.getService().updateWindow(mCurrentX, mCurrentY);
        }
        return true;
      }
    }
    return super.onTouchEvent(event);
  }


  public void reset() {
    Config config = Config.get();
    components = (List<Map<String,Object>>)config.getValue("window");
    if (config.hasKey("layout/max_entries")) max_entries = config.getInt("layout/max_entries");
    candidate_use_cursor = config.getBoolean("candidate_use_cursor");
    show_comment = config.getBoolean("show_comment");
    text_size = config.getPixel("text_size");
    candidate_text_size = config.getPixel("candidate_text_size");
    comment_text_size = config.getPixel("comment_text_size");
    label_text_size = config.getPixel("label_text_size");

    text_color = config.getColor("text_color");
    candidate_text_color = config.getColor("candidate_text_color");
    comment_text_color = config.getColor("comment_text_color");
    hilited_text_color = config.getColor("hilited_text_color");
    hilited_candidate_text_color = config.getColor("hilited_candidate_text_color");
    hilited_comment_text_color = config.getColor("hilited_comment_text_color");
    label_color = config.getColor("label_color");
    
    back_color = config.getColor("back_color");
    hilited_back_color = config.getColor("hilited_back_color");
    hilited_candidate_back_color = config.getColor("hilited_candidate_back_color");
    
    key_text_size = config.getPixel("key_text_size");
    key_text_color = config.getColor("key_text_color");
    key_back_color = config.getColor("key_back_color");
    
    float line_spacing_multiplier = config.getFloat("layout/line_spacing_multiplier");
    if (line_spacing_multiplier == 0f) line_spacing_multiplier = 1f;
    setLineSpacing(config.getFloat("layout/line_spacing"), line_spacing_multiplier);
    setMinWidth(config.getPixel("layout/min_width"));
    setMinHeight(config.getPixel("layout/min_height"));
    setMaxWidth(config.getPixel("layout/max_width"));
    setMaxHeight(config.getPixel("layout/max_height"));
    int margin_x, margin_y;
    margin_x = config.getPixel("layout/margin_x");
    margin_y = config.getPixel("layout/margin_y");
    setPadding(margin_x, margin_y, margin_x, margin_y);
    boolean show = config.getBoolean("show_window");
    setVisibility(show ? View.VISIBLE : View.GONE);
    max_length = config.getInt("layout/max_length");
    sticky_lines = config.getInt("layout/sticky_lines");
    movable = config.getString("layout/movable");
    all_phrases = config.getBoolean("layout/all_phrases");
    tfLabel = config.getFont("label_font");
    tfText = config.getFont("text_font");
    tfCandidate = config.getFont("candidate_font");
    tfComment = config.getFont("comment_font");
  }

  private Object getAlign(Map m) {
    Layout.Alignment i = Layout.Alignment.ALIGN_NORMAL;
    if (m.containsKey("align")) {
      String align = Function.getString(m, "align");
      switch (align) {
        case "left":
        case "normal":
          i = Layout.Alignment.ALIGN_NORMAL;
          break;
        case "right":
        case "opposite":
          i = Layout.Alignment.ALIGN_OPPOSITE;
          break;
        case "center":
          i = Layout.Alignment.ALIGN_CENTER;
          break;
      }
    }
    return new AlignmentSpan.Standard(i);
  }

  private void appendComposition(Map m) {
    Rime.RimeComposition r = Rime.getComposition();
    String s = r.getText();
    int start, end;
    String sep = Function.getString(m, "start");
    if (!Function.isEmpty(sep)) {
      start = ss.length();
      ss.append(sep);
      end = ss.length();
      ss.setSpan(getAlign(m), start, end, span);
    }
    start = ss.length();
    ss.append(s);
    end = ss.length();
    ss.setSpan(getAlign(m), start, end, span);
    composition_pos[0] = start;
    composition_pos[1] = end;
    ss.setSpan(new CompositionSpan(), start, end, span);
    ss.setSpan(new AbsoluteSizeSpan(text_size), start, end, span);
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP && m.containsKey("letter_spacing")) {
      Object o = m.get("letter_spacing");
      double size = 1d;
      if (o instanceof Integer) size = ((Integer)o).doubleValue();
      else if (o instanceof Float) size = ((Float)o).doubleValue();
      else if (o instanceof Double) size = ((Double)o).doubleValue();
      ss.setSpan(new LetterSpacingSpan((float)size), start, end, span);
    }
    start = composition_pos[0] + r.getStart();
    end = composition_pos[0] + r.getEnd();
    ss.setSpan(new ForegroundColorSpan(hilited_text_color), start, end, span);
    ss.setSpan(new BackgroundColorSpan(hilited_back_color), start, end, span);
    sep = Function.getString(m, "end");
    if (!Function.isEmpty(sep))ss.append(sep);
  }

  private int appendCandidates(Map m, int length) {
    int start, end;
    int start_num = 0;
    Rime.RimeCandidate[] candidates = Rime.getCandidates();
    if (candidates == null) return start_num;
    String sep = Function.getString(m, "start");
    highlightIndex = candidate_use_cursor ? Rime.getCandHighlightIndex() : -1;
    String label_format = Function.getString(m, "label");
    String candidate_format = Function.getString(m, "candidate");
    String comment_format = Function.getString(m, "comment");
    String line = Function.getString(m, "sep");
    int last_cand_length = 0;
    int line_length = 0;
    String[] labels = Rime.getSelectLabels();
    int i = -1;
    candidate_num = 0;
    for (Rime.RimeCandidate o: candidates) {
      String cand = o.text;
      if (Function.isEmpty(cand)) cand = "";
      i++;
      if (candidate_num >= max_entries) {
        if (start_num == 0 && candidate_num == i) start_num = candidate_num;
        break;
      }
      if (cand.length() < length) {
        if (start_num == 0 && candidate_num == i) start_num = candidate_num;
        if (all_phrases) continue;
        else break;
      }
      cand = String.format(candidate_format, cand);
      String line_sep;
      if (candidate_num == 0) {
        line_sep = sep;
      } else if ((sticky_lines > 0 && sticky_lines >= i)
            || (max_length > 0 && line_length + cand.length() > max_length)){
        line_sep = "\n";
        line_length = 0;
      } else {
        line_sep = line;
      }
      if (!Function.isEmpty(line_sep)) {
        start = ss.length();
        ss.append(line_sep);
        end = ss.length();
        ss.setSpan(getAlign(m), start, end, span);
      }
      if (!Function.isEmpty(label_format) && labels != null) {
        String label = String.format(label_format, labels[i]);
        start = ss.length();
        ss.append(label);
        end = ss.length();
        ss.setSpan(new CandidateSpan(i, tfLabel, hilited_candidate_text_color, hilited_candidate_back_color, label_color), start, end, span);
        ss.setSpan(new AbsoluteSizeSpan(label_text_size), start, end, span);
      }
      start = ss.length();
      ss.append(cand);
      end = ss.length();
      line_length += cand.length();
      ss.setSpan(getAlign(m), start, end, span);
      ss.setSpan(new CandidateSpan(i, tfCandidate, hilited_candidate_text_color, hilited_candidate_back_color, candidate_text_color), start, end, span);
      ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
      String comment = o.comment;
      if (show_comment && !Function.isEmpty(comment_format) && !Function.isEmpty(comment)) {
        comment = String.format(comment_format, comment);
        start = ss.length();
        ss.append(comment);
        end = ss.length();
        ss.setSpan(getAlign(m), start, end, span);
        ss.setSpan(new CandidateSpan(i, tfComment, hilited_comment_text_color, hilited_candidate_back_color, comment_text_color), start, end, span);
        ss.setSpan(new AbsoluteSizeSpan(comment_text_size), start, end, span);
        line_length += comment.length();
      }
      candidate_num++;
    }
    if (start_num == 0 && candidate_num == i + 1) start_num = candidate_num;
    sep = Function.getString(m, "end");
    if (!Function.isEmpty(sep)) ss.append(sep);
    return start_num;
  }

  private void appendButton(Map m) {
    if (m.containsKey("when")) {
      String when = Function.getString(m, "when");
      if (when.contentEquals("paging") && !Rime.isPaging()) return;
      if (when.contentEquals("has_menu") && !Rime.hasMenu()) return;
    }
    String label;
    Event e = new Event(null, Function.getString(m, "click"));
    if (m.containsKey("label")) label = Function.getString(m, "label");
    else label = e.getLabel();
    int start, end;
    String sep = null;
    if (m.containsKey("start")) sep = Function.getString(m, "start");
    if (!Function.isEmpty(sep)) {
      start = ss.length();
      ss.append(sep);
      end = ss.length();
      ss.setSpan(getAlign(m), start, end, span);
    }
    start = ss.length();
    ss.append(label);
    end = ss.length();
    ss.setSpan(getAlign(m), start, end, span);
    ss.setSpan(new EventSpan(e), start, end, span);
    ss.setSpan(new AbsoluteSizeSpan(key_text_size), start, end, span);
    sep = Function.getString(m, "end");
    if (!Function.isEmpty(sep)) ss.append(sep);
  }

  private void appendMove(Map m) {
    String s = Function.getString(m, "move");
    int start, end;
    String sep = Function.getString(m, "start");
    if (!Function.isEmpty(sep)) {
      start = ss.length();
      ss.append(sep);
      end = ss.length();
      ss.setSpan(getAlign(m), start, end, span);
    }
    start = ss.length();
    ss.append(s);
    end = ss.length();
    ss.setSpan(getAlign(m), start, end, span);
    move_pos[0] = start;
    move_pos[1] = end;
    ss.setSpan(new AbsoluteSizeSpan(key_text_size), start, end, span);
    ss.setSpan(new ForegroundColorSpan(key_text_color), start, end, span);
    sep = Function.getString(m, "end");
    if (!Function.isEmpty(sep))ss.append(sep);
  }

  public int setWindow(int length) {
    if (getVisibility() != View.VISIBLE) return 0;
    Rime.RimeComposition r = Rime.getComposition();
    if (r == null) return 0;
    String s = r.getText();
    if (Function.isEmpty(s)) return 0;
    setSingleLine(true); //設置單行
    ss = new SpannableStringBuilder();
    int start_num = 0;
    for (Map<String,Object> m: components) {
      if (m.containsKey("composition")) appendComposition(m);
      else if (m.containsKey("candidate")) start_num = appendCandidates(m, length);
      else if (m.containsKey("click"))appendButton(m);
      else if (m.containsKey("move"))appendMove(m);
    }
    if (candidate_num > 0 || ss.toString().contains("\n")) setSingleLine(false); //設置單行
    setText(ss);
    setMovementMethod(LinkMovementMethod.getInstance());
    return start_num;
  }
}
