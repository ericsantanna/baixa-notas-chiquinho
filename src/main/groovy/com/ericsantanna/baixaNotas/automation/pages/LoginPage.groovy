package com.ericsantanna.baixaNotas.automation.pages

import geb.Page

class LoginPage extends Page {
    static url = "https://www.itfgestor.com.br/ITFGestor/publico/login.jsf"
    static atCheckWaiting = true

    static at = {
        loginButton && loginButton.text()?.toLowerCase() == 'entrar'
    }

    static content = {
        form { $('form', name: 'formLogin') }
        loginField { form.find('input', type: 'email', 0) }
        passwordField { form.find('input', type: 'password', 0) }
        loginButton { form.find('button', type: 'submit', 0) }
    }
}
