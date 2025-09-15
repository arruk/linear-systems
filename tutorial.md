# CHISEL TUTORIAL

## 1. Introdução
- Uma linguagem de descrição de hardware (HDL – Hardware Description Language) é uma ferramenta que permite especificar circuitos digitais por meio de código textual, descrevendo tanto a estrutura (como portas lógicas, registradores e conexões) quanto o comportamento (como operações sequenciais e combinacionais) de um sistema. Diferente de linguagens de programação tradicionais, uma HDL não descreve uma sequência de instruções a serem executadas por um processador, mas sim a lógica que será sintetizada em hardware real — seja em uma FPGA ou em um ASIC. Tradicionalmente, linguagens como VHDL e Verilog dominaram esse espaço, mas com o avanço das metodologias de projeto surgiu a necessidade de ferramentas mais expressivas e integráveis ao ecossistema de software moderno. É nesse contexto que surge o Chisel (Constructing Hardware In a Scala Embedded Language), uma linguagem de descrição de hardware de alto nível baseada em Scala, que permite descrever circuitos de forma concisa, parametrizável e reutilizável. O Chisel combina a expressividade da programação funcional e orientada a objetos com a precisão necessária para gerar RTL em Verilog, oferecendo um fluxo de trabalho poderoso tanto para pesquisa acadêmica quanto para desenvolvimento industrial.

### Do Chisel ao Verilog: o fluxo de geração de hardware

---

O Chisel é uma linguagem de descrição de hardware *embedded* em Scala, ou seja, todo código escrito em Chisel é, na prática, um programa Scala que, quando executado, constrói uma árvore de hardware em memória. Esse processo funciona em diferentes etapas.

#### 1. Escrita do código em Chisel (Scala)  
Na etapa de escrita do código, o desenvolvedor descreve módulos, entradas, saídas e lógica combinacional ou sequencial usando classes e objetos Scala. Um exemplo simples seria um somador parametrizável, definido como uma classe que herda de `Module` e especifica seu `io`.

#### 2. Elaboração (Elaboration)  
Em seguida, ocorre a elaboração (elaboration). Quando o programa Scala é executado (via `sbt run` ou um teste), o Chisel interpreta a construção do hardware e gera uma representação intermediária chamada FIRRTL (*Flexible Intermediate Representation for RTL*). Nesse ponto, o hardware já está descrito em nível de registradores e conexões, mas ainda de forma mais abstrata que o Verilog.

#### 3. Transformações em FIRRTL
O compilador FIRRTL aplica então uma série de transformações e otimizações, que incluem verificações de tipo, simplificação de expressões e inferência de larguras de sinais. Esse processo garante que o circuito seja consistente, sem ambiguidades e pronto para síntese.

#### 4. Emissão de Verilog (Backend) 
Após esses passes, o FIRRTL é convertido em Verilog RTL, a linguagem padrão compreendida pelas ferramentas de síntese de FPGA e ASIC. O código gerado está pronto para ser utilizado em simuladores Verilog, como o Verilator, ou em ferramentas de síntese como Quartus, Vivado, Synopsys e Cadence.

#### 5. Integração com fluxo de síntese  
Por fim, o Verilog gerado pode ser integrado ao fluxo de síntese. Ele pode ser simulado para validação funcional, sintetizado para FPGA (mapeado em LUTs, flip-flops e blocos DSP) ou para ASIC (mapeado em portas da biblioteca de células padrão).

Em resumo, o fluxo consiste em escrever o código em Scala/Chisel, executar o programa para gerar FIRRTL, aplicar as transformações necessárias e emitir Verilog pronto para uso em fluxos de FPGA e ASIC.


### Benefícios do uso de uma linguagem de alto nível

---

O uso de uma linguagem de descrição de hardware de alto nível como o Chisel traz benefícios significativos em comparação às HDLs tradicionais.  

Uma das principais vantagens é que grande parte da complexidade de projeto pode ser tratada ainda na fase de elaboração, antes da geração do Verilog. Nesse estágio, o desenvolvedor pode explorar recursos como parametrização, reuso de código, composição modular e até mesmo metaprogramação em Scala para descrever circuitos de forma mais abstrata e expressiva.  

Um exemplo emblemático é o **Diplomacy**, utilizado no ecossistema Chipyard/Rocket-Chip. Esse recurso resolve automaticamente a configuração e interconexão de barramentos complexos como TileLink, ajustando larguras, protocolos e topologias sem que o projetista precise lidar diretamente com fios e sinais de baixo nível.  

Dessa forma, o projetista trabalha em um nível conceitual mais próximo da arquitetura do sistema, enquanto o compilador Chisel/FIRRTL se encarrega de expandir essas descrições em Verilog detalhado. O resultado é uma redução significativa de erros, maior rapidez na exploração de alternativas de projeto e a possibilidade de construir sistemas complexos de maneira mais eficiente e confiável.

Outro benefício importante está na separação clara entre a descrição do comportamento e a elaboração do hardware. Ao projetista cabe descrever **o que o circuito deve fazer** em um nível mais abstrato, usando a linguagem de alto nível para capturar a lógica e a estrutura do sistema. À ferramenta especializada (FIRRTL e backends do Chisel) cabe a tarefa de **como gerar** o Verilog final, aplicando otimizações, inferências de largura e transformações que muitas vezes seriam trabalhosas ou propensas a erro se feitas manualmente. 

Dessa forma, o Verilog produzido tende a ser mais consistente, otimizado e menos sujeito a falhas humanas, permitindo que o projetista concentre esforços no comportamento funcional e arquitetural, enquanto a ferramenta assegura uma implementação robusta em baixo nível.

## 2. Motivação 

### Motivação

A resolução de sistemas lineares é um problema clássico em ciência e engenharia, e sua implementação em hardware envolve a utilização de diversas estruturas fundamentais. Ao explorar esse problema, é possível abordar de forma prática os principais blocos que compõem circuitos digitais e entender como eles se organizam em arquiteturas mais complexas.  

Em primeiro lugar, operações básicas como soma e subtração podem ser mapeadas em **circuitos combinacionais**, servindo de exemplo inicial para entender a construção de operadores aritméticos e como eles se conectam em módulos maiores. Na sequência, operações mais elaboradas como multiplicação e divisão exigem a presença de **circuitos sequenciais**, que introduzem o conceito de latência, temporização e controle de dados ao longo do tempo.  

Além disso, ao se utilizar **máquinas de estado finitas (FSMs)** para organizar métodos diretos ou iterativos de solução, o estudante tem contato com uma das estruturas mais importantes em projeto digital, responsável por controlar o fluxo de execução e coordenar as diferentes etapas de cálculo. Esse ponto é particularmente relevante quando se considera a implementação de algoritmos de solução iterativa, como métodos de Jacobi ou Gauss-Seidel.  

Outro aspecto motivador é que a solução de sistemas lineares envolve operações que também aparecem em **processadores modernos**, como o uso de **operações em ponto flutuante**, manipulação de **vetores** para representar equações, e sinais de controle que coordenam a execução em arquiteturas mais sofisticadas. Assim, o estudo desse problema permite não apenas praticar a construção de módulos de hardware isolados, mas também visualizar como esses blocos estão presentes em projetos reais de CPUs, GPUs e aceleradores de propósito específico.  

Por fim, ao longo deste tutorial utilizaremos **exemplos reais de classes implementadas no processador Rocket**, não apenas para nos familiarizar com a descrição de hardware em Chisel, mas também para desenvolver a capacidade de ler e entender como projetos sérios e amplamente utilizados na academia e na indústria aplicam a linguagem na construção de hardware complexo.

### Ferramentas utilizadas

Este tutorial faz uso de um conjunto de ferramentas já consolidadas no ecossistema do Chisel:

- **Chisel 6**: linguagem de descrição de hardware de alto nível, embutida em Scala, que permite descrever circuitos digitais de forma parametrizável, reutilizável e expressiva. É a base para todas as implementações apresentadas.  
- **sbt (Scala Build Tool)**: ferramenta de automação de build para projetos Scala. É utilizada para compilar o código, gerenciar dependências e executar testes de forma integrada.  
- **ScalaTest**: biblioteca de testes que possibilita escrever testbenches de maneira clara e estruturada, permitindo validar o comportamento dos módulos de hardware descritos em Chisel.  

O repositório fornecido já disponibiliza um **template pronto**, contendo a configuração completa dessas ferramentas, além de exemplos básicos, testes automatizados e exercícios funcionais. Isso permite que o leitor inicie imediatamente a prática sem a necessidade de ajustes complexos no ambiente de desenvolvimento, concentrando-se no aprendizado e na experimentação dos conceitos de hardware.

#### Aviso importante

Durante o desenvolvimento e execução dos exemplos apresentados neste tutorial, é possível que ocorram **erros de compilação, execução ou configuração**. Isso faz parte natural do processo de aprendizado ao lidar com linguagens de descrição de hardware e ferramentas de automação.  

Longe de ser um obstáculo, esses erros devem ser vistos como **oportunidades de aprendizado**. Encorajamos o leitor a investigar as mensagens de erro, consultar a documentação, revisar o código e aplicar correções. Esse processo fortalece a familiaridade com o projeto, melhora a compreensão das ferramentas e aproxima a experiência prática do que ocorre em projetos reais de hardware.  

### Esquema-base: do sistema linear à execução em hardware
```math
\begin{aligned}
m_{21} &= \frac{a_{21}}{a_{11}} \\
a'_{22} &= a_{22} - m_{21}a_{12} \\
f'_2 &= f_2 - m_{21}f_1 \\
\\
x_2 &= \frac{f'_2}{a'_{22}} \\
x_1 &= \frac{f_1 - a_{12}x_2}{a_{11}}
\end{aligned}
```

## 2. Somadores e Subtratores Simples

### 2.1. Somador de 1 bit
- Conceito de Half Adder
- Conceito de Full Adder
- Implementação em Chisel
- Testbench simples

### 2.2. Somador de 16 bits
- Ripple Carry Adder
- Classe `Adder16` em Chisel
- Instanciação em `Comb`
- Testbench com casos aleatórios

### 2.3. Subtração
- Uso direto do operador `-`
- Implementação via two’s complement
- Comparação prática

---

## 3. Parametrização

### 3.1. Somador Parametrizado
- Classe `Adder(nBits: Int)`
- Exemplo `CombParam`
- Testbench com múltiplos valores de `nBits`

### 3.2. Benefícios
- Reuso
- Escalabilidade (8, 16, 32, 64 bits)
- Adaptação para diferentes arquiteturas

---

## 4. Overflow e Underflow

### 4.1. Operações sem sinal (UInt)
- Comportamento de wrap-around
- Flag `underflow` para subtração

### 4.2. Operações com sinal (SInt)
- Intervalos representáveis
- Overflow em complemento de dois
- Implementação de flags `addOverflow` e `subOverflow`

### 4.3. Testes de Canto
- Exemplo: `127 + 1` em 8 bits
- Exemplo: `-128 - 1` em 8 bits

---

## 5. ALU do Rocket Core

### 5.1. Estrutura
- Entradas: `in1`, `in2`, `fn`, `dw`
- Saídas: `out`, `adder_out`, `cmp_out`
- Constantes `FN_ADD`, `FN_SUB`, `DW_32`, `DW_64`

### 5.2. Wrapper
- Classe `RocketALUWrapper`
- Uso de parâmetros (`XLen`)
- Integração com projetos

### 5.3. Teste em Chisel
- ADD
- SUB
- Comparação com implementações próprias

---

## 6. FPGAs vs ASICs

### 6.1. Implementação em FPGAs
- Mapeamento em LUTs e carry chains
- Uso de operadores `+`/`-`
- Flags de overflow

### 6.2. Implementação em ASICs
- Arquiteturas de somadores: ripple-carry, carry-lookahead, carry-select, Kogge-Stone
- Trade-off: área vs tempo
- Overflow “de graça” no hardware

### 6.3. Comparação
- FPGA: síntese automática é suficiente
- ASIC: customização pode ser necessária

---

## 7. Conclusão
- Resumo das implementações vistas
- Quando usar operadores de alto nível
- Quando projetar manualmente
- Próximos passos de estudo

---

