# A language server implementation for Fuzion

## Requirements
- java version 17 or higher
- GNU-Make
- wget

## Run
- make

## Debug
- make debug

## Usage
### Transport socket
- run `make`
- connect the client to the (random) port the server prints to stdout.
### Transport stdio
- run `make stdio`

## Tests
- make run_tests

## Clients
|Client|Repository|
|---|---|
|vscode|https://github.com/tokiwa-software/vscode-fuzion|
|vim||
|emacs|see instructions below|
|eclipse (theia)|https://github.com/tokiwa-software/vscode-fuzion|

### Emacs
- Note: fuzionlsp_stdio (from ./bin/) needs to be in $PATH
- install lsp-mode, flycheck and company for emacs using
    - M-x package-install RET lsp-mode
    - M-x package-install RET flycheck
    - M-x package-install RET company RET
- add the following code to ~/.emacs.d/fuzion-lsp.el to enable [https://github.com/emacs-lsp/lsp-mode](lsp-mode)

```lisp
(require 'package)
(add-to-list 'package-archives '("melpa" . "https://melpa.org/packages/") t)
(add-to-list 'package-archives '("elpa" . "https://elpa.gnu.org/packages/"))
(package-initialize)
(custom-set-variables
 ;; custom-set-variables was added by Custom.
 ;; If you edit it by hand, you could mess it up, so be careful.
 ;; Your init file should contain only one such instance.
 ;; If there is more than one, they won't work right.
 '(inhibit-startup-screen t)
 '(package-selected-packages '(lsp-ui company flycheck lsp-mode ##)))
(custom-set-faces
 ;; custom-set-faces was added by Custom.
 ;; If you edit it by hand, you could mess it up, so be careful.
 ;; Your init file should contain only one such instance.
 ;; If there is more than one, they won't work right.
 )

(define-derived-mode fuzion-mode
  fundamental-mode "Fuzion"
  "Major mode for Fuzion.")

(add-to-list 'auto-mode-alist '("\\.fz\\'" . fuzion-mode))

(require 'lsp-mode)
(global-flycheck-mode)
(add-to-list 'lsp-language-id-configuration '(fuzion-mode . "fuzion"))

(defgroup lsp-fuzionlsp nil
  "LSP support for Fuzion, using fuzionlsp."
  :group 'lsp-mode
  :link '(url-link ""))

(lsp-register-client
 (make-lsp-client :new-connection (lsp-stdio-connection "fuzionlsp_stdio")
                  :major-modes '(fuzion-mode)
                  :priority -1
                  :server-id 'fuzionls))


(lsp-consistency-check lsp-fuzion)

(add-hook 'fuzion-mode-hook #'lsp)
(add-hook 'after-init-hook 'global-company-mode)

(provide 'lsp-fuzion)

(provide 'init)
;;; init.el ends here
```

- add following line to ~/.emacs.d/init.el or to ~/.emacs

  (load "~/.emacs.d/fuzion-lsp.el")

## Implementation state

|Feature|Status|
|---|---|
|diagnostics|☑|
|completion|☑|
|completion resolve|☐|
|hover|☑|
|signatureHelp|☐|
|declaration|☐|
|definition|☑|
|typeDefinition|☐|
|implementation|☐|
|references|☑|
|documentHighlight|☐|
|documentSymbol|☑|
|codeAction|☐|
|codeAction resolve|☐|
|codeLens|☐|
|codeLens resolve|☐|
|codeLens refresh|☐|
|documentLink|☐|
|documentLink resolve|☐|
|documentColor|☐|
|colorPresentation|☐|
|formatting|☐|
|rangeFormatting|☐|
|onTypeFormatting|☐|
|rename|☑|
|prepareRename|☑|
|foldingRange|☐|
|selectionRange|☐|
|prepareCallHierarchy|☐|
|callHierarchy incoming|☐|
|callHierarchy outgoing|☐|
|semantic tokens|☐|
|linkedEditingRange|☐|
|moniker|☐|
