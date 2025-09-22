# Projeto em Chisel para Solução de Sistemas Lineares

---

## Crie o seu próprio projeto em Chisel3

### Dependências

#### JDK 11 ou mais recente
Recomendamos usar o Java 11 ou versões LTS mais recentes. Embora o Chisel funcione com Java 8, nossa ferramenta de build preferida, o **Mill**, requer Java 11.  
Você pode instalar o JDK conforme o recomendado para o seu sistema operacional ou utilizar os binários pré-compilados do [Adoptium](https://adoptium.net/) (anteriormente AdoptOpenJDK).

#### SBT ou Mill
O **SBT** é a ferramenta de build mais comum na comunidade Scala. Você pode baixá-la [aqui](https://www.scala-sbt.org/download.html).  
O **Mill** é outra ferramenta de build para Scala/Java preferida pelos desenvolvedores do Chisel.  
Este repositório já inclui um script de inicialização `./mill`, de forma que nenhuma instalação adicional é necessária.  
Saiba mais no site oficial: [mill-build.org](https://mill-build.org).

#### Verilator
Os testes com `svsim` precisam do **Verilator** instalado.  
Veja as instruções de instalação [aqui](https://verilator.org/guide/latest/install.html).

---

## Verifique se tudo está funcionando

Os comandos do **SBT** são utilizados para compilar, testar e gerenciar o projeto em Scala/Chisel. O comando básico `sbt test` executa **todos os testes disponíveis** no projeto, garantindo a validação completa do código. Em situações de depuração, no entanto, pode ser mais prático rodar apenas um teste unitário específico utilizando `sbt "testOnly pacote.NomeDoTeste"`, substituindo `NomeDoTeste` pelo nome da classe de teste (por exemplo, `AdderSpec`) e pacote pelo package utilizado. Além disso, é possível criar novos comandos personalizados no `build.sbt`; neste projeto, por exemplo, criamos o comando `welcome` para rodar diretamente o teste de boas-vindas (`WelcomeSpec`), teste rodar `sbt welcome`.

```sbt

lazy val welcome = taskKey[Unit]("Run the welcome test")

welcome := {
  println("Running Welcome test...")
  (Test / testOnly).toTask(" *WelcomeSpec").value
}

```

Além dos testes, o **SBT** também fornece outros comandos úteis para o ciclo de desenvolvimento. O comando `sbt compile` compila o projeto e verifica se o código Scala/Chisel não possui erros de sintaxe ou tipagem, sem necessariamente rodar testes ou gerar hardware. O comando `sbt run` executa a aplicação principal definida no projeto (por exemplo, um objeto `Main` em Scala que instancie um módulo ou gere o Verilog correspondente). Já o comando `sbt clean` remove todos os arquivos gerados de compilação, forçando uma reconstrução completa na próxima vez que o projeto for compilado. Esses três comandos (`compile`, `run`, `clean`) são fundamentais para manter o ciclo de edição, compilação e execução de forma organizada.
