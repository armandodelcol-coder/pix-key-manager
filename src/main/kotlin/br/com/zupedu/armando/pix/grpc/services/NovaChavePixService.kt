package br.com.zupedu.armando.pix.grpc.services

import br.com.zupedu.armando.core.handler.exceptions.BadRequestErrorException
import br.com.zupedu.armando.core.handler.exceptions.ChavePixJaExisteException
import br.com.zupedu.armando.httpclients.BcbClient
import br.com.zupedu.armando.httpclients.ItauErpClient
import br.com.zupedu.armando.pix.grpc.dtos.RegistrarChavePixDto
import br.com.zupedu.armando.pix.model.ChavePix
import br.com.zupedu.armando.pix.repository.ChavePixRepository
import io.micronaut.validation.Validated
import org.slf4j.LoggerFactory
import javax.inject.Singleton
import javax.validation.Valid

@Singleton
@Validated
class NovaChavePixService(
    private val repository: ChavePixRepository,
    private val itauErpClient: ItauErpClient,
    private val bcbClient: BcbClient
) {
    private val logger = LoggerFactory.getLogger(NovaChavePixService::class.java)

    fun registra(@Valid novaChavePixDto: RegistrarChavePixDto): ChavePix {
        // Verificar existencia do cliente
        val clienteResponse = itauErpClient.buscarCliente(novaChavePixDto.clienteId)
        when (clienteResponse.status.code) {
            200 -> logger.info("Cliente encontrado.")
            else -> throw BadRequestErrorException("Cliente não encontrado.")
        }

        if (!novaChavePixDto.chave.isNullOrBlank() && repository.existsByChave(novaChavePixDto.chave)) throw ChavePixJaExisteException("Chave ${novaChavePixDto.chave} já existe.")

        // Verificar se o cliente possúi o tipo de conta informado
        logger.info("Verificando existência da conta para o cliente ${novaChavePixDto.clienteId} no itau.")
        val clienteContaResponse = itauErpClient.buscarContaCliente(novaChavePixDto.clienteId, novaChavePixDto.tipoConta.name)
        val conta = clienteContaResponse.body()?.toModel() ?: throw BadRequestErrorException("Tipo de conta não encontrada para o cliente no Itau.")

        val chavePix = novaChavePixDto.toModel(conta)
        val chavePixBcb = bcbClient.criarChavePix(chavePix.toCriarPixRequest()).body() ?: throw BadRequestErrorException("Não foi possível cadastrar a chave pix no BCB.")
        chavePix.chave = chavePixBcb.key
        repository.save(chavePix)
        logger.info("Chave Pix Gerada com sucesso.")
        return chavePix
    }
}
