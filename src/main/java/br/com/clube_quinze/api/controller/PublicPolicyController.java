package br.com.clube_quinze.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/policies")
public class PublicPolicyController {

    private static final String PRIVACY_AND_TERMS_HTML = """
<!DOCTYPE html>
<html lang=\"pt-BR\">
<head>
    <meta charset=\"utf-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
    <title>Clube Quinze - Política de Privacidade e Termos de Uso</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 24px; line-height: 1.6; color: #222; }
        h1, h2, h3 { color: #0b3d91; }
        h1 { font-size: 28px; margin-bottom: 12px; }
        h2 { font-size: 22px; margin-top: 32px; }
        h3 { font-size: 18px; margin-top: 24px; }
        p { margin: 12px 0; }
        ul { padding-left: 20px; }
        li { margin: 8px 0; }
        a { color: #0b3d91; }
        .updated { font-style: italic; color: #555; }
    </style>
</head>
<body>
    <h1>Clube Quinze</h1>
    <p class=\"updated\">Última atualização: 14 de novembro de 2026</p>

    <h2>Visão Geral</h2>
    <p>Este documento descreve a Política de Privacidade e os Termos de Uso do aplicativo Clube Quinze (\"Aplicativo\"). Ao utilizar nossos serviços, você concorda integralmente com as condições aqui dispostas.</p>

    <h2>Dados Coletados e Finalidades</h2>
    <h3>Cadastro e Autenticação</h3>
    <ul>
        <li>Dados pessoais: nome completo, endereço de e-mail, telefone e senha (armazenada de forma criptografada).</li>
        <li>Finalidade: criação de conta, autenticação, contato sobre sua conta e personalização da experiência.</li>
    </ul>
    <h3>Preferências e Perfil</h3>
    <ul>
        <li>Preferências de atendimento, fotos de perfil e galeria, feedbacks e interações em comunidade.</li>
        <li>Finalidade: oferecer serviços personalizados, recomendações e funcionalidades sociais.</li>
    </ul>
    <h3>Pagamentos e Assinaturas</h3>
    <ul>
        <li>Histórico de planos contratados, registros de pagamentos e status das transações.</li>
        <li>Finalidade: faturamento, gestão de planos, suporte e controle de inadimplência.</li>
    </ul>
    <h3>Registros de Uso</h3>
    <ul>
        <li>Logs técnicos, identificadores de sessão, tokens de autenticação e metadados de requisições.</li>
        <li>Finalidade: segurança, prevenção a fraudes, auditoria e monitoramento da qualidade do serviço.</li>
    </ul>

    <h2>Uso de Funcionalidades do Dispositivo</h2>
    <h3>Câmera</h3>
    <p>Utilizamos a câmera do dispositivo para permitir o envio de fotos na galeria do Clube Quinze, atualização da foto de perfil e anexos em postagens da comunidade. O acesso ocorre somente sob comando do usuário e as imagens são enviadas via conexão segura.</p>
    <h3>Armazenamento</h3>
    <p>Armazenamos arquivos localmente para cache temporário de mídias, comprovantes e outros conteúdos gerados pelo usuário. O armazenamento é usado exclusivamente para facilitar uploads, downloads e funcionamento offline limitado.</p>
    <h3>Notificações Push</h3>
    <p>Coletamos o token de push do dispositivo para enviar alertas de agendamentos, atualizações de conteúdo, mensagens administrativas e ofertas relevantes. Você pode gerenciar as permissões de notificações nas configurações do sistema.</p>

        <h2>Integração com o Google Agenda (opcional)</h2>
    <p>A administração do Clube Quinze pode, de forma opcional, conectar uma conta Google ao aplicativo para manter os agendamentos do clube sincronizados em uma agenda do Google Agenda. Esta integração é restrita a contas de administradores e nunca é ativada para os demais usuários.</p>
    <ul>
        <li><strong>O que acessamos:</strong> a lista de agendas da conta conectada (somente para que o administrador escolha onde os agendamentos serão salvos) e os eventos criados pelo próprio aplicativo na agenda escolhida.</li>
        <li><strong>O que enviamos ao Google:</strong> para cada agendamento, o título do atendimento, o nome do cliente, a data, o horário, a duração e as observações do agendamento. Esses dados já existem no Clube Quinze e são gravados apenas na agenda que o administrador escolheu.</li>
        <li><strong>Permissões solicitadas:</strong> criar agendas secundárias, listar as agendas da conta e criar, alterar e excluir eventos. Não lemos e-mails, contatos, arquivos nem outros dados da conta Google.</li>
        <li><strong>Armazenamento e segurança:</strong> as credenciais de acesso (tokens) são armazenadas criptografadas e usadas apenas para sincronizar a agenda do clube.</li>
        <li><strong>Desconexão:</strong> o administrador pode desconectar a conta a qualquer momento nas configurações do aplicativo ou em myaccount.google.com/permissions. Ao desconectar, apagamos as credenciais. Eventos já criados permanecem na agenda do Google e podem ser removidos pelo próprio titular da conta.</li>
    </ul>
    <p>O uso e a transferência, para qualquer outro aplicativo, das informações recebidas das APIs do Google obedecerão à <a href="https://developers.google.com/terms/api-services-user-data-policy">Política de Dados do Usuário dos Serviços de API do Google</a>, incluindo os requisitos de Uso Limitado. Não vendemos esses dados, não os usamos para publicidade nem os transferimos a terceiros, exceto quando necessário para operar a sincronização, cumprir a lei ou com o consentimento do titular, e nenhuma pessoa lê esses dados, exceto se necessário para suporte, segurança ou obrigação legal.</p>

    <h2>Compartilhamento de Dados</h2>
    <ul>
        <li>Prestadores de serviço essenciais (hospedagem, e-mail, gateways de pagamento) sob contratos que garantem confidencialidade e segurança.</li>
        <li>Autoridades públicas mediante obrigações legais ou ordens judiciais.</li>
        <li>Nenhum dado é vendido a anunciantes ou terceiros para fins de marketing.</li>
    </ul>

    <h2>Retenção e Exclusão</h2>
    <p>Mantemos os dados enquanto sua conta estiver ativa e conforme exigido por leis fiscais e regulatórias. Você pode solicitar a exclusão da conta e dos dados pessoais por meio do suporte; removeremos ou anonimizaremos as informações, salvo quando a retenção for obrigatória.</p>

    <h2>Segurança</h2>
    <p>Adotamos criptografia em repouso e em trânsito, segregação de ambientes, autenticação baseada em tokens JWT e controles de acesso com hierarquia de perfis. Logs de segurança são monitorados e revistos periodicamente.</p>

    <h2>Direitos do Usuário</h2>
    <ul>
        <li>Acessar, corrigir ou atualizar seus dados pessoais.</li>
        <li>Solicitar portabilidade e exclusão, observado o prazo legal.</li>
        <li>Revogar consentimentos referentes a notificações e compartilhamentos opcionais.</li>
        <li>Contestar tratamentos ilegítimos junto à Autoridade Nacional de Proteção de Dados (ANPD).</li>
    </ul>

    <h2>Termos de Uso</h2>
    <h3>Conta e Responsabilidades</h3>
    <ul>
        <li>Você é responsável por manter suas credenciais confidenciais e notificar o suporte sobre qualquer uso não autorizado.</li>
        <li>A conta é de uso pessoal; é vedado ceder, alugar ou comercializar o acesso à plataforma.</li>
    </ul>
    <h3>Conteúdo Gerado pelo Usuário</h3>
    <ul>
        <li>É proibido publicar conteúdos ilegais, ofensivos, discriminatórios ou que violem direitos de terceiros.</li>
        <li>O usuário garante possuir os direitos sobre qualquer mídia enviada e concede licença não exclusiva para uso no contexto do serviço.</li>
    </ul>
    <h3>Serviços Oferecidos</h3>
    <ul>
        <li>Gerenciamento de agendamentos, planos de assinatura e preferências de atendimento.</li>
        <li>Comunidade interna para compartilhamento de conteúdos e feedbacks.</li>
        <li>Processamento de notificações, recomendações e comunicações transacionais.</li>
    </ul>
    <h3>Suspensão e Encerramento</h3>
    <p>Reservamo-nos o direito de suspender ou encerrar contas em caso de violação destes termos, uso fraudulento ou risco à segurança da plataforma.</p>

    <h2>Alterações</h2>
    <p>Esta política pode ser atualizada periodicamente. Notificaremos os usuários por canais apropriados quando houver mudanças relevantes. O uso contínuo do Aplicativo após a atualização implica concordância com os novos termos.</p>

    <h2>Contato</h2>
    <p>Em caso de dúvidas ou solicitações relacionadas a privacidade e termos, entre em contato pelo e-mail <a href=\"mailto:privacidade@clubequinze.com\">privacidade@clubequinze.com</a>.</p>
</body>
</html>
""";

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> privacyPolicy() {
        // browsers can cache the document safely for one day
        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)))
                .body(PRIVACY_AND_TERMS_HTML);
    }
}
