package key.secretkey.utils;

import android.support.annotation.NonNull;
import android.view.View;

import key.secretkey.SelectFolderFragment;
import key.secretkey.crypto.PgpHandler;

import java.util.ArrayList;

/**   CODE EMPRUNTÉ
 *      Les lignes suivantes proviennent du projet open source
 *      Android-Password-Store sous license GPL 3.0 de l'auteur Zeapo
 */
public class FolderRecyclerAdapter extends EntryRecyclerAdapter {
    private final SelectFolderFragment.OnFragmentInteractionListener listener;

    // Provide a suitable constructor (depends on the kind of dataset)
    public FolderRecyclerAdapter(PgpHandler activity, SelectFolderFragment.OnFragmentInteractionListener listener, ArrayList<PasswordItem> values) {
        super(activity, values);
        this.listener = listener;
    }

    @NonNull
    protected View.OnClickListener getOnClickListener(final ViewHolder holder, final PasswordItem pass) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onFragmentInteraction(pass);
                notifyItemChanged(holder.getAdapterPosition());
            }
        };
    }

}
/*
 *  FIN DU CODE EMPRUNTÉ
 */