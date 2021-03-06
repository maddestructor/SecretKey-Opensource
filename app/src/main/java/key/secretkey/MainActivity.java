package key.secretkey;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import key.secretkey.utils.PasswordItem;
import key.secretkey.utils.PasswordRecyclerAdapter;
import key.secretkey.utils.PasswordStorage;
import key.secretkey.crypto.PgpHandler;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PwdStrAct";
    private File currentDir;
    private SharedPreferences settings;
    private Activity activity;
    private PasswordFragment plist;
    private AlertDialog selectDestinationDialog;
    private ShortcutManager shortcutManager;

    private final static int CLONE_REPO_BUTTON = 401;
    private final static int NEW_REPO_BUTTON = 402;
    private final static int HOME = 403;

    private final static int REQUEST_EXTERNAL_STORAGE = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        
        activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);
//        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        
    }
    

    public void onResume() {
        super.onResume();
        // do not attempt to checkLocalRepository() if no storage permission: immediate crash
        if (settings.getBoolean("git_external", false)) {
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Snackbar snack = Snackbar.make(findViewById(R.id.main_layout), "The store is on the sdcard but the app does not have permission to access it. Please give permission.",
                            Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.dialog_ok, new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    ActivityCompat.requestPermissions(activity,
                                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                            REQUEST_EXTERNAL_STORAGE);
                                }
                            });
                    snack.show();
                    View view = snack.getView();
                    TextView tv = (TextView) view.findViewById(android.support.design.R.id.snackbar_text);
                    tv.setTextColor(Color.WHITE);
                    tv.setMaxLines(10);
                } else {
                    // No explanation needed, we can request the permission.
                    ActivityCompat.requestPermissions(activity,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            REQUEST_EXTERNAL_STORAGE);
                }
            } else {
                checkLocalRepository();
            }

        } else {
            checkLocalRepository();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            try {
                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settingsIntent);
            } catch (Exception e) {
                System.out.println("Le lancement de l'activité de paramètre a planté(");
                e.printStackTrace();
            }
        }
        

        return super.onOptionsItemSelected(item);
    }

    /****CODE EMPRUNTÉ****/
    /* Les lignes suivantes proviennent du projet open source */
    /* Android-Password-Store sous license GPL 3.0 de l'auteur Zeapo */
    /* Ce sont principalement des méthodes servant a effectuer des opérations sur les mot de passe */

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkLocalRepository();
                }
            }
        }
    }

    public void createNewRepository() {
        initRepository(NEW_REPO_BUTTON);
    }

    private void createRepository() {
        if (!PasswordStorage.isInitialized()) {
            PasswordStorage.initialize(this);
        }

        File localDir = PasswordStorage.getRepositoryDirectory(getApplicationContext());

        localDir.mkdir();
        try {
            PasswordStorage.createRepository(localDir);
            new File(localDir.getAbsolutePath() + "/.gpg-id").createNewFile();
            settings.edit().putBoolean("repository_initialized", true).apply();
        } catch (Exception e) {
            e.printStackTrace();
            localDir.delete();
            return;
        }
        checkLocalRepository();
    }

    public void initializeRepositoryInfo() {
        if (settings.getBoolean("git_external", false) && settings.getString("git_external_repo", null) != null) {
            File dir = new File(settings.getString("git_external_repo", null));

            if (dir.exists() && dir.isDirectory() && !FileUtils.listFiles(dir, null, true).isEmpty() &&
                    !PasswordStorage.getPasswords(dir, PasswordStorage.getRepositoryDirectory(this)).isEmpty()) {
                PasswordStorage.closeRepository();
                checkLocalRepository();
                return; // if not empty, just show me the passwords!
            }
        }

        final Set<String> keyIds = settings.getStringSet("openpgp_key_ids_set", new HashSet<String>());

        if (keyIds.isEmpty())
            new AlertDialog.Builder(this)
                    .setMessage(this.getResources().getString(R.string.key_dialog_text))
                    .setPositiveButton(this.getResources().getString(R.string.dialog_positive), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(activity, SettingsActivity.class);
                            startActivityForResult(intent, 104);
                        }
                    })
                    .setNegativeButton(this.getResources().getString(R.string.dialog_negative), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            // do nothing :(
                        }
                    })
                    .show();

        createRepository();
    }

    private void checkLocalRepository() {
        Repository repo = PasswordStorage.initialize(this);
        if (repo == null) {
            Intent intent = new Intent(activity, SettingsActivity.class);
            intent.putExtra("operation", "git_external");
            startActivityForResult(intent, HOME);
        } else {
            checkLocalRepository(PasswordStorage.getRepositoryDirectory(getApplicationContext()));
        }
    }

    private void checkLocalRepository(File localDir) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (localDir != null && settings.getBoolean("repository_initialized", false)) {
            Log.d("PASS", "Check, dir: " + localDir.getAbsolutePath());
            // do not push the fragment if we already have it
            if (fragmentManager.findFragmentByTag("PasswordsList") == null || settings.getBoolean("repo_changed", false)) {
                settings.edit().putBoolean("repo_changed", false).apply();

                plist = new PasswordFragment();
                Bundle args = new Bundle();
                args.putString("Path", PasswordStorage.getRepositoryDirectory(getApplicationContext()).getAbsolutePath());

                // if the activity was started from the autofill settings, the
                // intent is to match a clicked pwd with app. pass this to fragment
                if (getIntent().getBooleanExtra("matchWith", false)) {
                    args.putBoolean("matchWith", true);
                }

                plist.setArguments(args);

                getSupportActionBar().show();
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);

                fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

                fragmentTransaction.replace(R.id.main_layout, plist, "PasswordsList");
                fragmentTransaction.commit();
            }
        } else {
//            getSupportActionBar().hide();

            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
//            Snackbar snack = Snackbar.make(findViewById(R.id.main_layout), "You fucked up, you shouldn't be here XD",
//                    Snackbar.LENGTH_INDEFINITE);
//            snack.show();

            createNewRepository();

//            ToCloneOrNot cloneFrag = new ToCloneOrNot();
//            fragmentTransaction.replace(R.id.main_layout, cloneFrag, "ToCloneOrNot");
//            fragmentTransaction.commit();
        }
    }

    public void decryptPassword(PasswordItem item) {
        Intent intent = new Intent(this, PgpHandler.class);
        intent.putExtra("NAME", item.toString());
        intent.putExtra("FILE_PATH", item.getFile().getAbsolutePath());
        intent.putExtra("Operation", "DECRYPT");

        startActivityForResult(intent, PgpHandler.REQUEST_CODE_DECRYPT_AND_VERIFY);
    }

    public void editPassword(PasswordItem item) {
        Intent intent = new Intent(this, PgpHandler.class);
        intent.putExtra("NAME", item.toString());
        intent.putExtra("FILE_PATH", item.getFile().getAbsolutePath());
        intent.putExtra("Operation", "EDIT");
        startActivityForResult(intent, PgpHandler.REQUEST_CODE_EDIT);
    }

    public void createPassword() {
        if (!PasswordStorage.isInitialized()) {
            new AlertDialog.Builder(this)
                    .setMessage(this.getResources().getString(R.string.creation_dialog_text))
                    .setPositiveButton(this.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    }).show();
            return;
        }

        if (settings.getStringSet("openpgp_key_ids_set", new HashSet<String>()).isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("OpenPGP key not selected")
                    .setMessage("We will redirect you to settings. Please select your OpenPGP Key.")
                    .setPositiveButton(this.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(activity, SettingsActivity.class);
                            startActivity(intent);
                        }
                    }).show();
            return;
        }

        this.currentDir = getCurrentDir();
        Log.i("PWDSTR", "Adding file to : " + this.currentDir.getAbsolutePath());

        Intent intent = new Intent(this, PgpHandler.class);
        intent.putExtra("FILE_PATH", getCurrentDir().getAbsolutePath());
        intent.putExtra("Operation", "ENCRYPT");
        startActivityForResult(intent, PgpHandler.REQUEST_CODE_ENCRYPT);
    }

    // deletes passwords in order from top to bottom
    public void deletePasswords(final PasswordRecyclerAdapter adapter, final Set<Integer> selectedItems) {
        final Iterator it = selectedItems.iterator();
        if (!it.hasNext()) {
            return;
        }
        final int position = (int) it.next();
        final PasswordItem item = adapter.getValues().get(position);
        new AlertDialog.Builder(this).
                setMessage(this.getResources().getString(R.string.delete_dialog_text) +
                        item + "\"")
                .setPositiveButton(this.getResources().getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        item.getFile().delete();
                        adapter.remove(position);
                        it.remove();
                        adapter.updateSelectedItems(position, selectedItems);

//                        commit("[ANDROID PwdStore] Remove " + item + " from store.");
                        deletePasswords(adapter, selectedItems);
                    }
                })
                .setNegativeButton(this.getResources().getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        it.remove();
                        deletePasswords(adapter, selectedItems);
                    }
                })
                .show();
    }

    public void movePasswords(ArrayList<PasswordItem> values) {
        Intent intent = new Intent(this, PgpHandler.class);
        ArrayList<String> fileLocations = new ArrayList<>();
        for (PasswordItem passwordItem : values) {
            fileLocations.add(passwordItem.getFile().getAbsolutePath());
        }
        intent.putExtra("Files", fileLocations);
        intent.putExtra("Operation", "SELECTFOLDER");
        startActivityForResult(intent, PgpHandler.REQUEST_CODE_SELECT_FOLDER);
    }

    /**
     * clears adapter's content and updates it with a fresh list of passwords from the root
     */
    public void updateListAdapter() {
        if ((null != plist)) {
            plist.updateAdapter();
        }
    }

    /**
     * Updates the adapter with the current view of passwords
     */
    public void refreshListAdapter() {
        if ((null != plist)) {
            plist.refreshAdapter();
        }
    }

//    public void filterListAdapter(String filter) {
//        if ((null != plist)) {
//            plist.filterAdapter(filter);
//        }
//    }

    private File getCurrentDir() {
        if ((null != plist)) {
            return plist.getCurrentDir();
        }
        return PasswordStorage.getRepositoryDirectory(getApplicationContext());
    }

//    private void commit(final String message) {
//        new GitOperation(PasswordStorage.getRepositoryDirectory(activity), activity) {
//            @Override
//            public void execute() {
//                Log.d(TAG, "Commiting with message " + message);
//                Git git = new Git(this.repository);
//                GitAsyncTask tasks = new GitAsyncTask(activity, false, true, this);
//                tasks.execute(
//                        git.add().addFilepattern("."),
//                        git.commit().setMessage(message)
//                );
//            }
//        }.execute();
//    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
//                case GitActivity.REQUEST_CLONE:
//                    // if we get here with a RESULT_OK then it's probably OK :)
//                    settings.edit().putBoolean("repository_initialized", true).apply();
//                    break;
                case PgpHandler.REQUEST_CODE_DECRYPT_AND_VERIFY:
                    // if went from decrypt->edit and user saved changes, we need to commit
                    if (data.getBooleanExtra("needCommit", false)) {
//                        commit(this.getResources().getString(R.string.edit_commit_text) + data.getExtras().getString("NAME"));
                        refreshListAdapter();
                    }
                    break;
                case PgpHandler.REQUEST_CODE_ENCRYPT:
//                    commit(this.getResources().getString(R.string.add_commit_text) + data.getExtras().getString("NAME") + this.getResources().getString(R.string.from_store));
                    refreshListAdapter();
                    break;
                case PgpHandler.REQUEST_CODE_EDIT:
//                    commit(this.getResources().getString(R.string.edit_commit_text) + data.getExtras().getString("NAME"));
                    refreshListAdapter();
                    break;
//                case GitActivity.REQUEST_INIT:
//                    initializeRepositoryInfo();
//                    break;
//                case GitActivity.REQUEST_SYNC:
//                case GitActivity.REQUEST_PULL:
//                    updateListAdapter();
//                    break;
                case HOME:
                    checkLocalRepository();
                    break;
//                case NEW_REPO_BUTTON:
//                    initializeRepositoryInfo();
//                    break;
//                case CLONE_REPO_BUTTON:
//                    // duplicate code
//                    if (settings.getBoolean("git_external", false) && settings.getString("git_external_repo", null) != null) {
//                        String externalRepoPath = settings.getString("git_external_repo", null);
//                        File dir = externalRepoPath != null ? new File(externalRepoPath) : null;
//
//                        if (dir != null &&
//                                dir.exists() &&
//                                dir.isDirectory() &&
//                                !FileUtils.listFiles(dir, null, true).isEmpty() &&
//                                !PasswordStorage.getPasswords(dir, PasswordStorage.getRepositoryDirectory(this)).isEmpty()) {
//                            PasswordStorage.closeRepository();
//                            checkLocalRepository();
//                            return; // if not empty, just show me the passwords!
//                        }
//                    }
//                    Intent intent = new Intent(activity, GitActivity.class);
//                    intent.putExtra("Operation", GitActivity.REQUEST_CLONE);
//                    startActivityForResult(intent, GitActivity.REQUEST_CLONE);
//                    break;
//                case PgpHandler.REQUEST_CODE_SELECT_FOLDER:
//                    Log.d("Moving", "Moving passwords to " + data.getStringExtra("SELECTED_FOLDER_PATH"));
//                    Log.d("Moving", TextUtils.join(", ", data.getStringArrayListExtra("Files")));
//                    File target = new File(data.getStringExtra("SELECTED_FOLDER_PATH"));
//                    if (!target.isDirectory()) {
//                        Log.e("Moving", "Tried moving passwords to a non-existing folder.");
//                        break;
//                    }
//
//                    for (String string : data.getStringArrayListExtra("Files")) {
//                        File source = new File(string);
//                        if (!source.exists()) {
//                            Log.e("Moving", "Tried moving something that appears non-existent.");
//                            continue;
//                        }
//                        if (!source.renameTo(new File(target.getAbsolutePath() + "/" + source.getName()))) {
//                            // TODO this should show a warning to the user
//                            Log.e("Moving", "Something went wrong while moving.");
//                        } else {
//                            commit("[ANDROID PwdStore] Moved "
//                                    + string.replace(PasswordStorage.getRepositoryDirectory(getApplicationContext()) + "/", "")
//                                    + " to "
//                                    + target.getAbsolutePath().replace(PasswordStorage.getRepositoryDirectory(getApplicationContext()) + "/", "")
//                                    + target.getAbsolutePath() + "/" + source.getName() + ".");
//                        }
//                    }
//                    updateListAdapter();
//                    break;
            }
        }
    }

    protected void initRepository(final int operation) {
        PasswordStorage.closeRepository();

        new AlertDialog.Builder(this)
                .setTitle("Repository location")
                .setMessage("Select where to create or clone your password repository.")
                .setPositiveButton("Hidden (preferred)", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        settings.edit().putBoolean("git_external", false).apply();

                        switch (operation) {
                            case NEW_REPO_BUTTON:
                                initializeRepositoryInfo();
                                break;
//                            case CLONE_REPO_BUTTON:
//                                PasswordStorage.initialize(PasswordStore.this);
//
//                                Intent intent = new Intent(activity, GitActivity.class);
//                                intent.putExtra("Operation", GitActivity.REQUEST_CLONE);
//                                startActivityForResult(intent, GitActivity.REQUEST_CLONE);
//                                break;
                        }
                    }
                })
                .setNegativeButton("SD-Card", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        settings.edit().putBoolean("git_external", true).apply();

                        if (settings.getString("git_external_repo", null) == null) {
                            Intent intent = new Intent(activity, SettingsActivity.class);
                            intent.putExtra("operation", "git_external");
                            startActivityForResult(intent, operation);
                        } else {
                            new AlertDialog.Builder(activity).
                                    setTitle("Directory already selected").
                                    setMessage("Do you want to use \"" + settings.getString("git_external_repo", null) + "\"?").
                                    setPositiveButton("Use", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            switch (operation) {
                                                case NEW_REPO_BUTTON:
                                                    initializeRepositoryInfo();
                                                    break;
//                                                case CLONE_REPO_BUTTON:
//                                                    PasswordStorage.initialize(PasswordStore.this);
//
//                                                    Intent intent = new Intent(activity, GitActivity.class);
//                                                    intent.putExtra("Operation", GitActivity.REQUEST_CLONE);
//                                                    startActivityForResult(intent, GitActivity.REQUEST_CLONE);
//                                                    break;
                                            }
                                        }
                                    }).
                                    setNegativeButton("Change", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent(activity, SettingsActivity.class);
                                            intent.putExtra("operation", "git_external");
                                            startActivityForResult(intent, operation);
                                        }
                                    }).show();
                        }
                    }
                })
                .show();
    }

    public void matchPasswordWithApp(PasswordItem item) {
        String path = item.getFile().getAbsolutePath();
        path = path.replace(PasswordStorage.getRepositoryDirectory(getApplicationContext()) + "/", "").replace(".gpg", "");
        Intent data = new Intent();
        data.putExtra("path", path);
        setResult(RESULT_OK, data);
        finish();
    }
}
