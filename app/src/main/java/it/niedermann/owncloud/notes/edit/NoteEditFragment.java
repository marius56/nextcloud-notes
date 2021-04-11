package it.niedermann.owncloud.notes.edit;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.databinding.FragmentNoteEditBinding;
import it.niedermann.owncloud.notes.shared.model.CloudNote;
import it.niedermann.owncloud.notes.shared.model.ISyncCallback;

import static androidx.core.view.ViewCompat.isAttachedToWindow;
import static it.niedermann.owncloud.notes.shared.util.NoteUtil.getFontSizeFromPreferences;

public class NoteEditFragment extends SearchableBaseNoteFragment {

    private static final String TAG = NoteEditFragment.class.getSimpleName();

    private static final String LOG_TAG_AUTOSAVE = "AutoSave";

    private static final long DELAY = 2000; // Wait for this time after typing before saving
    private static final long DELAY_AFTER_SYNC = 5000; // Wait for this time after saving before checking for next save

    private FragmentNoteEditBinding binding;

    private Handler handler;
    private boolean saveActive;
    private boolean unsavedEdit;
    private final Runnable runAutoSave = new Runnable() {
        @Override
        public void run() {
            if (unsavedEdit) {
                Log.d(LOG_TAG_AUTOSAVE, "runAutoSave: start AutoSave");
                autoSave();
            } else {
                Log.d(LOG_TAG_AUTOSAVE, "runAutoSave: nothing changed");
            }
        }
    };
    private TextWatcher textWatcher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_edit).setVisible(false);
        menu.findItem(R.id.menu_preview).setVisible(true);
    }

    @Override
    public ScrollView getScrollView() {
        return binding.scrollView;
    }

    @Override
    protected void scrollToY(int y) {
        if (binding != null) {
            binding.scrollView.post(() -> binding.scrollView.setScrollY(y));
        }
    }

    @Override
    protected Layout getLayout() {
        binding.editContent.onPreDraw();
        return binding.editContent.getLayout();
    }

    @Override
    protected FloatingActionButton getSearchNextButton() {
        return binding.searchNext;
    }

    @Override
    protected FloatingActionButton getSearchPrevButton() {
        return binding.searchPrev;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentNoteEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Nothing to do here...
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Nothing to do here...
            }

            @Override
            public void afterTextChanged(final Editable s) {
                unsavedEdit = true;
                if (!saveActive) {
                    handler.removeCallbacks(runAutoSave);
                    handler.postDelayed(runAutoSave, DELAY);
                }
            }
        };

        if (note != null) {
            if (note.getContent().isEmpty()) {
                binding.editContent.requestFocus();

                requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

                final InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(getView(), InputMethodManager.SHOW_IMPLICIT);
                } else {
                    Log.e(TAG, InputMethodManager.class.getSimpleName() + " is null.");
                }
            }

            binding.editContent.setMarkdownString(note.getContent());
            binding.editContent.setEnabled(true);

            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());
            binding.editContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, getFontSizeFromPreferences(requireContext(), sp));
            if (sp.getBoolean(getString(R.string.pref_key_font), false)) {
                binding.editContent.setTypeface(Typeface.MONOSPACE);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_remove_checked) {
            /*
            Example markdown string: "- [x] Item1\n    - [ ] Item2\n- [x] Item3"

            Regex explanation:
            (?:term1|term2)                 --> (?:...):    do not generate groups and search for term1 OR term2

            ^\s*[-*] \[[xX]\] [^\n]*\n?\r?  --> "^":        Pattern starts from the beginning of the string
                                                "\s*":      any number of blanks
                                                "[-*]":     followed by an "-" or an "*"
                                                "\[[xX]\]": "[x]" or "[X]"
                                                "[^\n]*":   any character except a new line
                                                "\n?\r?":   optional newline and carriage return at the end
                                                ==> removes a checked item in the first line e.g. "- [x] Item1", "* [X] Item2"

            \n\r?\s*[-*] \[[xX]\] [^\n]*    --> "\n\r?":    Pattern starts after a new line with optional carriage return
                                                "\s*":          any number of blanks
                                                "[-*]":         followed by an "-" or an "*"
                                                "\[[xX]\]":     "[x]" or "[X]"
                                                "[^\n]*":       any characters except a new line
                                                ==> removes the checked items after the first line "- [x] Item1", "* [X] Item2"
            */
            binding.editContent.setMarkdownString(note.getContent().replaceAll("(?:^\\s*[-*] \\[[xX]\\] [^\n]*\n?|\n\r?\\s*[-*] \\[[xX]\\] [^\n]*)", ""));
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.editContent.addTextChangedListener(textWatcher);
    }

    @Override
    public void onPause() {
        super.onPause();
        binding.editContent.removeTextChangedListener(textWatcher);
        cancelTimers();
    }

    private void cancelTimers() {
        handler.removeCallbacks(runAutoSave);
    }

    /**
     * Gets the current content of the EditText field in the UI.
     *
     * @return String of the current content.
     */
    @Override
    protected String getContent() {
        return binding.editContent.getText().toString();
    }

    @Override
    protected void saveNote(@Nullable ISyncCallback callback) {
        super.saveNote(callback);
        unsavedEdit = false;
    }

    /**
     * Saves the current changes and show the status in the ActionBar
     */
    private void autoSave() {
        Log.d(LOG_TAG_AUTOSAVE, "STARTAUTOSAVE");
        saveActive = true;
        saveNote(new ISyncCallback() {
            @Override
            public void onFinish() {
                onSaved();
            }

            @Override
            public void onScheduled() {
                onSaved();
            }

            private void onSaved() {
                // AFTER SYNCHRONIZATION
                Log.d(LOG_TAG_AUTOSAVE, "FINISHED AUTOSAVE");
                saveActive = false;

                // AFTER "DELAY_AFTER_SYNC" SECONDS: allow next auto-save or start it directly
                handler.postDelayed(runAutoSave, DELAY_AFTER_SYNC);

            }
        });
    }

    @Override
    protected void colorWithText(@NonNull String newText, @Nullable Integer current, int mainColor, int textColor) {
        if (binding != null && isAttachedToWindow(binding.editContent)) {
            binding.editContent.clearFocus();
            binding.editContent.setSearchText(newText, current);
        }
    }

    @Override
    public void applyBrand(int mainColor, int textColor) {
        super.applyBrand(mainColor, textColor);
        binding.editContent.setSearchColor(mainColor);
        binding.editContent.setHighlightColor(getTextHighlightBackgroundColor(requireContext(), mainColor, colorPrimary, colorAccent));
    }

    public static BaseNoteFragment newInstance(long accountId, long noteId) {
        final BaseNoteFragment fragment = new NoteEditFragment();
        final Bundle args = new Bundle();
        args.putLong(PARAM_NOTE_ID, noteId);
        args.putLong(PARAM_ACCOUNT_ID, accountId);
        fragment.setArguments(args);
        return fragment;
    }

    public static BaseNoteFragment newInstanceWithNewNote(CloudNote newNote) {
        final BaseNoteFragment fragment = new NoteEditFragment();
        final Bundle args = new Bundle();
        args.putSerializable(PARAM_NEWNOTE, newNote);
        fragment.setArguments(args);
        return fragment;
    }
}
