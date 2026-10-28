# Relatório — enxugamento do núcleo, onda 2

Pacote: `pushinbr/pam-native-auth`. Branch: `feat/port4-core-slim-20261028`. Versão pública: `0.4.1`.

## O que foi feito

- A capacidade opcional passou a pertencer ao plugin; SDK mínimo 1.46.0.
- O núcleo conserva aliases PHP depreciados por uma versão, até 1.47.
- Contratos nativos e dados tipados foram preservados; testes acompanham a implementação.
- Release: [0.4.1](https://github.com/push-in/pam-native-auth/releases/tag/v0.4.1). Atualização do Packagist solicitada e aceita pela API.

## Validação

- PHP: 4/4.
- Compile Kotlin conjunto do núcleo e cinco plugins: aprovado.
- Testes JVM conjuntos: 360/360, zero falhas.
- Compilação dos testes instrumentados conjuntos: aprovada; execução em dispositivo pendente.
- SDK PHP com contratos de compatibilidade dos plugins: aprovado.
- Swift: pendente; Mac proibido.
- QA em dispositivo e medição de CPU/PSS: pendentes; o emulador compartilhado estava ocupado. S10 não utilizado.

## Commits de implementação

```text
ecb786e fix: align AppCompat for biometric host test compilation [skip ci]
c40e6e7 feat: adapt the former core biometric API into auth.biometrics [skip ci]
4335772 Add non-secret vault existence checks (#6)
```

O commit deste relatório é posterior aos commits acima. Nenhuma release do núcleo 1.46.0 foi publicada.
