package com.chrisfry.socialq.userinterface.adapters

interface IItemSelectionListener<T> {
    fun onItemSelected(selectedItem: T)
}