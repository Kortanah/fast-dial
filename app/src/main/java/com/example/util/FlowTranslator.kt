package com.example.util

import java.util.Locale

object FlowTranslator {

    fun isFrenchActive(userLanguageSetting: String = ""): Boolean {
        if (userLanguageSetting.isNotBlank()) {
            return userLanguageSetting.equals("French", ignoreCase = true) || userLanguageSetting.equals("fr", ignoreCase = true)
        }
        val sysLang = Locale.getDefault().language.lowercase()
        return sysLang.startsWith("fr")
    }

    /**
     * Translates an English workflow or template string to French if French language is active.
     */
    fun translate(text: String, userLanguageSetting: String = ""): String {
        if (!isFrenchActive(userLanguageSetting) || text.isBlank()) return text

        val trimmed = text.trim()
        // Check exact match in pre-defined translations table first
        exactTranslations[trimmed]?.let { return it }

        // Fallback to phrase & word replacement rules for dynamic text
        var result = text
        for ((englishPhrase, frenchPhrase) in phraseReplacements) {
            result = result.replace(englishPhrase, frenchPhrase, ignoreCase = true)
        }
        return result
    }

    private val exactTranslations = mapOf(
        // Flow Names & Templates
        "MTN Mobile Money Transfer" to "Transfert Mobile Money MTN",
        "MTN MoMo Balance Check" to "Vérification du solde MTN MoMo",
        "Orange Money Transfer" to "Transfert Orange Money",
        "Orange Money Balance Check" to "Solde Orange Money",
        "Telecel Cash Transfer" to "Transfert Telecel Cash",
        "AirtelTigo Money Transfer" to "Transfert AirtelTigo Money",
        "Wave Money Transfer" to "Transfert d'argent Wave",
        "Moov Money Transfer" to "Transfert Moov Money",
        "Moov Money Balance Check" to "Vérification du solde Moov Money",
        "Vodafone Cash Transfer" to "Transfert Vodafone Cash",
        "Airtime Top-Up / Recharge" to "Recharge de crédit téléphonique",
        "Buy Airtime for Self" to "Acheter du crédit pour soi-même",
        "Buy Airtime for Others" to "Acheter du crédit pour un tiers",
        "Buy Internet Data Bundle" to "Achat de forfait Internet",
        "Buy Daily / Weekly Data" to "Acheter un pass Data Jour / Semaine",
        "Ecobank Mobile Banking" to "Banque Mobile Ecobank",
        "Bank Account Balance Check" to "Consultation du solde bancaire",
        "Bank to Wallet Transfer" to "Transfert Banque vers Portefeuille",
        "Wallet to Bank Transfer" to "Transfert Portefeuille vers Banque",
        "Pay Electricity Bill (ECG / CIE)" to "Payer la facture d'électricité (ECG / CIE)",
        "Pay Water Bill (GWCL / SODECI)" to "Payer la facture d'eau (GWCL / SODECI)",
        "Pay TV Subscription" to "Payer l'abonnement TV / Canal+",
        "Merchant Payment" to "Paiement marchand",
        "Withdrawal / Cash Out" to "Retrait d'argent / Cash Out",
        "Check Account Balance" to "Consulter le solde du compte",
        "Check Mobile Money Balance" to "Vérifier le solde Mobile Money",

        // Flow Descriptions
        "Transfer money to any MTN MoMo registered user or mobile wallet." to "Transférez de l'argent vers tout utilisateur enregistré MTN MoMo ou portefeuille mobile.",
        "Quickly check your current MTN Mobile Money wallet balance." to "Consultez rapidement le solde actuel de votre portefeuille MTN Mobile Money.",
        "Send funds to Orange Money users safely with automated prompts." to "Envoyez des fonds aux utilisateurs Orange Money en toute sécurité grâce aux étapes automatisées.",
        "Check your Orange Money balance in seconds." to "Consultez votre solde Orange Money en quelques secondes.",
        "Purchase mobile airtime credit directly for your line." to "Achetez du crédit de communication mobile directement pour votre ligne.",
        "Recharge mobile credit for family or friends." to "Rechargez du crédit mobile pour votre famille ou vos amis.",
        "Subscribe to daily, weekly, or monthly data packages." to "Souscrivez aux forfaits Internet journaliers, hebdomadaires ou mensuels.",
        "Access Ecobank mobile services and transfer funds." to "Accédez aux services mobiles Ecobank et effectuez des transferts de fonds.",
        "Check your connected bank account balance via USSD." to "Consultez le solde de votre compte bancaire connecté via USSD.",
        "Pay your monthly utility electricity bills seamlessly." to "Payez facilement vos factures d'électricité mensuelles.",
        "Pay water utility bills directly from your mobile wallet." to "Payez vos factures d'eau directement depuis votre portefeuille mobile.",

        // Node Titles
        "Start Node" to "Nœud de départ",
        "USSD Dial" to "Composition USSD",
        "User Prompt / Input" to "Saisie utilisateur",
        "Decision Rule" to "Règle de décision",
        "End Node" to "Nœud de fin",
        "Dial USSD Code" to "Composer le code USSD",
        "Select Option" to "Sélectionner l'option",
        "Enter Recipient Number" to "Saisir le numéro du destinataire",
        "Enter Amount" to "Saisir le montant",
        "Enter PIN" to "Saisir le code PIN",
        "Confirm Transfer" to "Confirmer le transfert",
        "Confirm Payment" to "Confirmer le paiement",
        "Confirm PIN" to "Confirmer le code PIN",
        "Select Network" to "Sélectionner le réseau",
        "Select Bundle" to "Sélectionner le forfait",
        "Select Account" to "Sélectionner le compte",
        "Enter Reference / Bill ID" to "Saisir la référence / l'ID facture",
        "Enter Account Number" to "Saisir le numéro de compte",
        "Workflow Execution Completed" to "Exécution du flux terminée",

        // Node Descriptions
        "Initiates the USSD workflow execution" to "Initie l'exécution du flux USSD",
        "Dials a USSD code (e.g., *170#)" to "Compose un code USSD (ex. *170#)",
        "Branches flow based on response text" to "Oriente le flux selon la réponse du serveur",
        "Terminates the USSD session" to "Termine la session USSD",
        "Automatic" to "Automatique",
        "Fixed auto-entered value" to "Valeur saisie automatiquement",
        "Input" to "Saisie",
        "Prompt user for input before run" to "Demander la saisie à l'utilisateur avant le lancement",
        "Manual" to "Manuel",
        "Wait for user to manually enter in time" to "Attendre la saisie manuelle dans le temps imparti",

        // Variables & Fields
        "Amount" to "Montant",
        "Recipient Number" to "Numéro du destinataire",
        "Phone Number" to "Numéro de téléphone",
        "PIN" to "Code PIN",
        "PIN Code" to "Code PIN",
        "Bill ID" to "ID Facture",
        "Account Number" to "Numéro de compte",
        "Reference" to "Référence",
        "Network Provider" to "Opérateur réseau",
        "Data Bundle" to "Forfait Data",
        "Number" to "Nombre",
        "Letter / Text" to "Lettre / Texte",
        "Phone Contact" to "Contact téléphonique"
    )

    private val phraseReplacements = listOf(
        "Transfer money to" to "Transférer de l'argent vers",
        "Transfer money" to "Transférer de l'argent",
        "Send money to" to "Envoyer de l'argent à",
        "Send money" to "Envoyer de l'argent",
        "Check balance" to "Consulter le solde",
        "Account balance" to "Solde du compte",
        "Balance check" to "Consultation du solde",
        "Check your" to "Vérifier votre",
        "Buy airtime for" to "Acheter du crédit pour",
        "Buy airtime" to "Acheter du crédit",
        "Airtime top-up" to "Recharge de crédit",
        "Airtime recharge" to "Recharge de crédit",
        "Buy data bundle" to "Acheter un forfait internet",
        "Buy data" to "Acheter des données",
        "Data bundle" to "Forfait internet",
        "Pay bill" to "Payer la facture",
        "Pay electricity" to "Payer l'électricité",
        "Pay water" to "Payer l'eau",
        "Enter recipient phone number" to "Saisir le numéro du destinataire",
        "Enter recipient number" to "Saisir le numéro du destinataire",
        "Enter phone number" to "Saisir le numéro de téléphone",
        "Enter recipient" to "Saisir le destinataire",
        "Enter amount" to "Saisir le montant",
        "Enter PIN" to "Saisir le code PIN",
        "Enter account number" to "Saisir le numéro de compte",
        "Select option" to "Sélectionner une option",
        "Select network" to "Sélectionner le réseau",
        "Select bundle" to "Sélectionner le forfait",
        "Confirm transaction" to "Confirmer la transaction",
        "Confirm transfer" to "Confirmer le transfert",
        "Confirm payment" to "Confirmer le paiement",
        "Mobile Money" to "Mobile Money",
        "Bank transfer" to "Transfert bancaire",
        "Cash out" to "Retrait d'argent",
        "Withdrawal" to "Retrait",
        "Recipient" to "Destinataire",
        "Amount" to "Montant",
        "Balance" to "Solde"
    )
}
