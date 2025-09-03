package com.guodong.android.jasmine.recipe

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guodong.android.jasmine.recipe.databinding.ItemClientBinding

/**
 * Created by guodongAndroid on 2025/9/3
 */
class ClientAdapter(
    private val clients: List<Client>
) : RecyclerView.Adapter<ClientAdapter.ViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemClientBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return clients.size
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(clients[position])
    }

    class ViewHolder(
        private val binding: ItemClientBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(client: Client) {
            binding.tvClientId.text = client.id
            binding.tvUsername.text = client.username
        }
    }
}